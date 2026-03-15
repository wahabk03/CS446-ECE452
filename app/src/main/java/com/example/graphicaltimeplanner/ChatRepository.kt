package com.example.graphicaltimeplanner

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class ChatSession(
    val id: String = "",
    val name: String = "New Chat",
    val createdAt: Timestamp = Timestamp.now(),
    val messages: List<ChatMessage> = emptyList()
)

object ChatRepository {
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()
    
    // Path: users/{uid}/agent/history/sessions
    private fun getCollection() = auth.currentUser?.let { user ->
        db.collection("users").document(user.uid)
          .collection("agent").document("history")
          .collection("sessions")
    }

    suspend fun getChatSessions(): List<ChatSession> {
        val col = getCollection() ?: return emptyList()
        return try {
            val snapshot = col.orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ChatSession::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createChatSession(): ChatSession {
        val col = getCollection() ?: throw Exception("User not logged in")
        
        // Temporarily name them Chat 1, Chat 2 etc based on count
        val count = try { col.get().await().size() } catch (e: Exception) { 0 }
        val sessionName = "Chat ${count + 1}"
        
        val session = ChatSession(name = sessionName)
        val doc = col.document()
        doc.set(session).await()
        return session.copy(id = doc.id)
    }

    suspend fun deleteChatSession(sessionId: String) {
        val col = getCollection() ?: return
        try {
            col.document(sessionId).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveChatMessages(sessionId: String, messages: List<ChatMessage>) {
        val col = getCollection() ?: return
        try {
            col.document(sessionId).update("messages", messages).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun updateChatName(sessionId: String, name: String) {
        val col = getCollection() ?: return
        try {
            col.document(sessionId).update("name", name).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
