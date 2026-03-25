package com.example.graphicaltimeplanner

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AgentApi {
    private const val BASE_URL = "http://10.0.2.2:5000/chat"
    private const val SUMMARIZE_URL = "http://10.0.2.2:5000/summarize"

    data class AgentResponse(val response: String, val showButton: Boolean)

    suspend fun sendMessage(
        context: Context,
        message: String,
        history: List<ChatMessage>,
        fileUri: Uri?,
        fileName: String?
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

            val url = URL(BASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(responseStr)
                val responseText = jsonObj.optString("response", "No response")
                val showButton = jsonObj.optBoolean("show_button", false)
                return@withContext AgentResponse(responseText, showButton)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                return@withContext AgentResponse("Error ($responseCode): $err", false)
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
            val url = URL(SUMMARIZE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(responseStr)
                return@withContext jsonObj.optString("summary", "Chat Session")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext "Chat Session"
    }
}
