package com.example.graphicaltimeplanner

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object AgentApi {
    private const val CHAT_STREAM_URL = "http://10.0.2.2:5000/chat_stream"
    private const val SUMMARIZE_URL = "http://10.0.2.2:5000/summarize"
    private const val GENERATE_EMAIL_URL = "http://10.0.2.2:5000/generate_email"
    private const val READ_TIMEOUT_MS = 0
    private const val CONNECT_TIMEOUT_MS = 30000
    private const val MAX_RETRIES = 2

    data class AgentResponse(val response: String, val showButton: Boolean)
    data class GeneratedEmailResponse(val subject: String, val body: String)

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
            val user = FirebaseAuth.getInstance().currentUser
            val uid = user?.uid ?: "anonymous"
            
            val jsonBody = JSONObject().apply {
                put("uid", uid)
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
                    val inputStream = context.contentResolver.openInputStream(fileUri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        put("file_bytes", base64Str)
                    }
                }
            }

            return@withContext withRetry {
                val url = URL(CHAT_STREAM_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Connection", "close")
                    connection.doOutput = true

                    val writer = OutputStreamWriter(connection.outputStream)
                    writer.write(jsonBody.toString())
                    writer.flush()
                    writer.close()

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
            e.printStackTrace()
            return@withContext AgentResponse("Error: ${e.message}", false)
        }
    }

    suspend fun summarizeChat(message: String): String = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("message", message)
            }
            return@withContext withRetry {
                val url = URL(SUMMARIZE_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Connection", "close")
                    connection.doOutput = true

                    val writer = OutputStreamWriter(connection.outputStream)
                    writer.write(jsonBody.toString())
                    writer.flush()
                    writer.close()

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
            e.printStackTrace()
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
            val jsonBody = JSONObject().apply {
                put("issue", issue)
                put("advisor_name", advisorName ?: "")
                put("program_name", programName ?: "")
                put("year_level", yearLevel ?: "")
                put("student_name", studentName ?: "")
                put("student_id", studentId ?: "")
            }

            return@withContext withRetry {
                val url = URL(GENERATE_EMAIL_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Connection", "close")
                    connection.doOutput = true

                    val writer = OutputStreamWriter(connection.outputStream)
                    writer.write(jsonBody.toString())
                    writer.flush()
                    writer.close()

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
            e.printStackTrace()
            return@withContext GeneratedEmailResponse(
                subject = "Request for Academic Advising Assistance",
                body = "Error generating email: ${e.message}"
            )
        }
    }
}
