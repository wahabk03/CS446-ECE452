package com.example.graphicaltimeplanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ChatStateManager {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var activeSession: ChatSession? by mutableStateOf(null)
    var sessionId: String? by mutableStateOf(null)
    var messages: List<ChatMessage> by mutableStateOf(emptyList())
    var isWaitingForAgent: Boolean by mutableStateOf(false)
    var showRedirectButton: Boolean by mutableStateOf(false)
    var redirectButtonCountdownProgress: Float by mutableStateOf(1f)
    var thinkingFoldVersion: Int by mutableStateOf(0)

    private val thinkingFoldBySession: MutableMap<String, MutableMap<String, Boolean>> = mutableMapOf()

    var newChatDraft: String by mutableStateOf("")
    private val sessionDrafts: MutableMap<String, String> = mutableMapOf()

    fun getDraftForSession(sessionId: String?): String {
        return if (sessionId == null) newChatDraft else (sessionDrafts[sessionId] ?: "")
    }

    fun setDraftForSession(sessionId: String?, draft: String) {
        if (sessionId == null) {
            newChatDraft = draft
        } else {
            sessionDrafts[sessionId] = draft
        }
    }

    fun clearDraftForSession(sessionId: String?) {
        if (sessionId == null) {
            newChatDraft = ""
        } else {
            sessionDrafts.remove(sessionId)
        }
    }

    private fun sessionKey(sessionId: String?): String {
        return sessionId ?: "__new_chat__"
    }

    fun isThinkingGroupExpanded(sessionId: String?, thinkingGroupId: String): Boolean {
        val foldMap = thinkingFoldBySession[sessionKey(sessionId)] ?: return false
        return foldMap[thinkingGroupId] == true
    }

    fun setThinkingGroupExpanded(sessionId: String?, thinkingGroupId: String, expanded: Boolean) {
        val foldMap = thinkingFoldBySession.getOrPut(sessionKey(sessionId)) { mutableMapOf() }
        val previous = foldMap[thinkingGroupId]
        foldMap[thinkingGroupId] = expanded
        if (previous != expanded) {
            thinkingFoldVersion += 1
        }
    }
}
