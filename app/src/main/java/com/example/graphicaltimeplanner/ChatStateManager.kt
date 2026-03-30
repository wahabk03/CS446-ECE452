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
}
