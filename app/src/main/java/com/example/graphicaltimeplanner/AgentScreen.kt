package com.example.graphicaltimeplanner

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ChatMessage(val role: String = "", val content: String = "", val attachedFileName: String? = null)

// Helper function to extract file name from URI
private fun Uri.getFileName(context: android.content.Context): String {
    var result: String? = null
    if (this.scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = this.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown file"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(onBack: () -> Unit, onHistoryClick: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputValue by remember { mutableStateOf("") }
    var attachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            attachedFileUri = it
            attachedFileName = it.getFileName(context)
        }
    }
    
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var sessionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ChatStateManager.activeSession?.id) {
        val session = ChatStateManager.activeSession
        if (session != null) {
            sessionId = session.id
            messages = session.messages
        } else {
            sessionId = null
            messages = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f))
    ) {
        // Top Decorative Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colorResource(R.color.uw_gold_lvl4))
        )

        // Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agent",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHistoryClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.List, contentDescription = "Chat History", tint = Color.DarkGray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Back", color = Color.White)
                }
            }
        }

        // Chat Messages Area
        if (messages.isEmpty()) {
            // Centered initial state
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "How can I help you today?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = colorResource(R.color.uw_gold_lvl4),
                    lineHeight = 36.sp
                )
            }
        } else {
            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
            items(messages.reversed()) { msg ->
                if (msg.role == "user") {
                    // User Message (Boxed, right-aligned)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (msg.attachedFileName != null) {
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Attachment", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = msg.attachedFileName, fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = colorResource(R.color.uw_gold_lvl4),
                                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    color = Color.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    // Agent Message (Unboxed, spanning width, plain layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Optional: You could add an AI avatar icon here
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = msg.content,
                                color = Color.Black,
                                fontSize = 15.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        }

        // Selected File Display
        if (attachedFileName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Attached File", tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = attachedFileName!!, modifier = Modifier.weight(1f), fontSize = 14.sp)
                IconButton(onClick = { 
                    attachedFileUri = null
                    attachedFileName = null
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove File", tint = Color.Gray)
                }
            }
        }

        // Input Row
        Surface(
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add File/Attachment")
                }
                
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the agent...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                IconButton(
                    onClick = {
                        if (inputValue.isNotBlank() || attachedFileUri != null) {
                            // Add user message
                            val currentMsgs = messages + ChatMessage("user", inputValue, attachedFileName)
                            messages = currentMsgs
                            val userMsg = inputValue
                            inputValue = ""
                            attachedFileUri = null
                            attachedFileName = null
                            
                            // Placeholder for actual Agent API call
                            val newMsgs = currentMsgs + ChatMessage("assistant", "I received your message. The Python AI agent will process this later!")
                            messages = newMsgs

                            // Save to Firestore
                            val currentSessionId = sessionId
                            coroutineScope.launch {
                                var idToSave = currentSessionId
                                if (idToSave == null) {
                                    val newSession = ChatRepository.createChatSession()
                                    idToSave = newSession.id
                                    sessionId = idToSave
                                    ChatStateManager.activeSession = newSession.copy(messages = newMsgs)
                                } else {
                                    ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(messages = newMsgs)
                                }
                                ChatRepository.saveChatMessages(idToSave!!, newMsgs)
                            }
                        }
                    },
                    enabled = inputValue.isNotBlank() || attachedFileUri != null
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = if (inputValue.isNotBlank() || attachedFileUri != null) colorResource(R.color.uw_gold_lvl4) else Color.Gray
                    )
                }
            }
        }
    }
}