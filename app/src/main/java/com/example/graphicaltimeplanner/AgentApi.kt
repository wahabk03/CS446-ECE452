package com.example.graphicaltimeplanner

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object AgentApi {
    private const val TAG = "AgentApi"
    private val agentBaseUrl = BuildConfig.AGENT_BASE_URL.trimEnd('/')
    private val chatStreamUrl = "$agentBaseUrl/chat_stream"
    private val summarizeUrl = "$agentBaseUrl/summarize"
    private val generateEmailUrl = "$agentBaseUrl/generate_email"
    private const val READ_TIMEOUT_MS = 120000
    private const val CONNECT_TIMEOUT_MS = 30000
    private const val MAX_RETRIES = 2
    private const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    private val allowedUploadMimeTypes = setOf(
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv"
    )
    private val allowedUploadExtensions = setOf(".pdf", ".txt", ".md", ".csv")

    data class AgentResponse(val response: String, val showButton: Boolean)
    data class GeneratedEmailResponse(val subject: String, val body: String)

    private suspend fun requireIdToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IOException("Please sign in before using the AI assistant.")
        return user.getIdToken(false).await().token
            ?: throw IOException("Unable to verify your sign-in. Please sign in again.")
    }

    private fun openAgentConnection(urlString: String, idToken: String): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.readTimeout = READ_TIMEOUT_MS
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $idToken")
        connection.setRequestProperty("Connection", "close")
        connection.doOutput = true
        return connection
    }

    private fun writeJson(connection: HttpURLConnection, jsonBody: JSONObject) {
        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(jsonBody.toString())
            writer.flush()
        }
    }

    private fun readUploadAsBase64(context: Context, fileUri: Uri, fileName: String): String {
        val mimeType = context.contentResolver.getType(fileUri)
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }

        val isAllowed = mimeType in allowedUploadMimeTypes || extension in allowedUploadExtensions
        if (!isAllowed) {
            throw IOException("Unsupported file type. Please upload a PDF, TXT, MD, or CSV file.")
        }

        queryFileSize(context, fileUri)?.let { size ->
            if (size > MAX_UPLOAD_BYTES) {
                throw IOException("File is too large. Maximum upload size is 5 MB.")
            }
        }

        var totalBytes = 0
        val bytes = context.contentResolver.openInputStream(fileUri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                totalBytes += read
                if (totalBytes > MAX_UPLOAD_BYTES) {
                    throw IOException("File is too large. Maximum upload size is 5 MB.")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException("Could not read the selected file.")

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun queryFileSize(context: Context, fileUri: Uri): Long? {
        context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1 && !cursor.isNull(index)) {
                    return cursor.getLong(index)
                }
            }
        }
        return null
    }

    private suspend fun <T> withRetry(block: () -> T): T {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= MAX_RETRIES) {
            try {
                return block()
            } catch (e: SocketTimeoutException) {
                lastException = e
            } catch (e: IOException) {
                lastException = e
            }

            attempt++
            if (attempt <= MAX_RETRIES) {
                // Short linear backoff to avoid immediate reconnect on transient server/socket drops.
                kotlinx.coroutines.delay(500L * attempt)
            }
        }

        throw (lastException ?: IOException("Request failed after retries"))
    }

    suspend fun sendMessage(
        context: Context,
        message: String,
        history: List<ChatMessage>,
        fileUri: Uri?,
        fileName: String?,
        onToolEvent: ((String) -> Unit)? = null
    ): AgentResponse = withContext(Dispatchers.IO) {
        try {
            val idToken = requireIdToken()
            
            val jsonBody = JSONObject().apply {
                put("message", message)
                
                val historyArray = JSONArray()
                // exclude the newly added user message from history if we want, or just send previous
                // Wait, AgentScreen.kt already appended it to messages. Let's pass the prior ones.
                // We'll trust history is proper.
                for (msg in history) {
                    if (msg.role == "tool_status") continue
                    val msgObj = JSONObject()
                    msgObj.put("role", msg.role)
                    msgObj.put("content", msg.content ?: "")
                    historyArray.put(msgObj)
                }
                put("history", historyArray)

                if (fileUri != null && fileName != null) {
                    put("file_name", fileName)
                    put("file_bytes", readUploadAsBase64(context, fileUri, fileName))
                }
            }

            return@withContext withRetry {
                val connection = openAgentConnection(chatStreamUrl, idToken)
                try {
                    writeJson(connection, jsonBody)

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        var responseText = "No response"
                        var showButton = false

                        connection.inputStream.bufferedReader().use { reader ->
                            while (true) {
                                val rawLine = reader.readLine() ?: break
                                val line = rawLine.trim()
                                if (line.isEmpty()) continue

                                try {
                                    val eventObj = JSONObject(line)
                                    when (eventObj.optString("type")) {
                                        "tool" -> {
                                            val toolMessage = eventObj.optString("message", "")
                                            if (toolMessage.isNotBlank()) {
                                                onToolEvent?.invoke(toolMessage)
                                            }
                                        }
                                        "final" -> {
                                            responseText = eventObj.optString("response", responseText)
                                            showButton = eventObj.optBoolean("show_button", showButton)
                                        }
                                        "error" -> {
                                            val err = eventObj.optString("message", "Unknown streaming error")
                                            responseText = "Error: $err"
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Ignore malformed stream lines and continue reading.
                                }
                            }
                        }

                        AgentResponse(responseText, showButton)
                    } else {
                        val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        AgentResponse("Error ($responseCode): $err", false)
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error sending agent message", e)
            return@withContext AgentResponse("Error: ${e.message}", false)
        }
    }

    suspend fun summarizeChat(message: String): String = withContext(Dispatchers.IO) {
        try {
            val idToken = requireIdToken()
            val jsonBody = JSONObject().apply {
                put("message", message)
            }
            return@withContext withRetry {
                val connection = openAgentConnection(summarizeUrl, idToken)
                try {
                    writeJson(connection, jsonBody)

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObj = JSONObject(responseStr)
                        jsonObj.optString("summary", "Chat Session")
                    } else {
                        "Chat Session"
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error summarizing chat", e)
        }
        return@withContext "Chat Session"
    }

    suspend fun generateAdvisorEmail(
        issue: String,
        advisorName: String?,
        programName: String?,
        yearLevel: String?,
        studentName: String?,
        studentId: String?
    ): GeneratedEmailResponse = withContext(Dispatchers.IO) {
        try {
            val idToken = requireIdToken()
            val jsonBody = JSONObject().apply {
                put("issue", issue)
                put("advisor_name", advisorName ?: "")
                put("program_name", programName ?: "")
                put("year_level", yearLevel ?: "")
                put("student_name", studentName ?: "")
                put("student_id", studentId ?: "")
            }

            return@withContext withRetry {
                val connection = openAgentConnection(generateEmailUrl, idToken)
                try {
                    writeJson(connection, jsonBody)

                    val responseCode = connection.responseCode
                    val responseStr = if (responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    }
                    val jsonObj = JSONObject(responseStr)

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        GeneratedEmailResponse(
                            subject = jsonObj.optString("subject", "Request for Academic Advising Assistance"),
                            body = jsonObj.optString("body", "")
                        )
                    } else {
                        val err = jsonObj.optString("error", "Unknown error")
                        GeneratedEmailResponse(
                            subject = "Request for Academic Advising Assistance",
                            body = "Error generating email: $err"
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error generating advisor email", e)
            return@withContext GeneratedEmailResponse(
                subject = "Request for Academic Advising Assistance",
                body = "Error generating email: ${e.message}"
            )
        }
    }
}
