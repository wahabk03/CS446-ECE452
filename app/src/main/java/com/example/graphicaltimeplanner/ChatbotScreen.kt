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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton

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
fun ChatbotScreen(
    onLogout: () -> Unit = {},
    onViewProfile: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToAdvisor: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onNavigateToTimetable: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputValue by remember { mutableStateOf("") }
    var attachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var isWaitingForAgent by remember { mutableStateOf(false) }
    var showRedirectButton by remember { mutableStateOf(false) }
    var redirectButtonCountdownProgress by remember { mutableFloatStateOf(1f) }
    
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

    LaunchedEffect(showRedirectButton) {
        if (!showRedirectButton) {
            redirectButtonCountdownProgress = 1f
            return@LaunchedEffect
        }

        val totalDurationMs = 10_000L
        val stepMs = 50L
        val startTime = System.currentTimeMillis()

        while (showRedirectButton) {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = (totalDurationMs - elapsed).coerceAtLeast(0L)
            redirectButtonCountdownProgress = remaining.toFloat() / totalDurationMs.toFloat()

            if (remaining <= 0L) break
            delay(stepMs)
        }
    }

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

    
    val primaryYellow = Color(0xFFFFD700)
    val lightYellow = primaryYellow.copy(alpha = 0.25f)
    val lightBackground = Color(0xFFFDFDFD)

    val displayName by AppState.displayName
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = lightBackground,
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.CHATBOT,
                onCoursesClick = onNavigateToCourses,
                onAiClick = onNavigateToAi,
                onScheduleClick = onNavigateToHome,
                onChatbotClick = {},
                onAdvisorClick = onNavigateToAdvisor
            )
        }
    ) { innerPadding ->
Column(
        modifier = Modifier
            .fillMaxSize().padding(innerPadding)
            .background(Color.White)
    ) {
        // Chatbot Header Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onViewProfile() }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(primaryYellow),
                    contentAlignment = Alignment.Center
                ) {
                    if (nameInitial != null) {
                        Text(nameInitial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(displayName.ifBlank { "User" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("View Profile", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLogout) {
                    Text(
                        text = "Logout",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // AI Status Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00695C)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "AI Assistant",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Always here to help",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.List, contentDescription = "History", tint = Color.Gray)
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
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray,
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
                ChatBubble(message = msg, primaryYellow = primaryYellow)
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

        // Action popup button
        if (showRedirectButton) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = {
                        showRedirectButton = false
                        onNavigateToTimetable()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .drawWithContent {
                            drawContent()
                            val progress = redirectButtonCountdownProgress.coerceIn(0f, 1f)
                            if (progress <= 0f) return@drawWithContent

                            val strokeWidth = 3.dp.toPx()
                            val corner = 24.dp.toPx()
                            val inset = strokeWidth / 2f

                            val roundedRectPath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = inset,
                                        top = inset,
                                        right = size.width - inset,
                                        bottom = size.height - inset,
                                        cornerRadius = CornerRadius(corner, corner)
                                    )
                                )
                            }

                            val pathMeasure = PathMeasure().apply {
                                setPath(roundedRectPath, false)
                            }

                            val segmentPath = Path()
                            val segmentStop = pathMeasure.length * progress
                            pathMeasure.getSegment(0f, segmentStop, segmentPath, true)

                            drawPath(
                                path = segmentPath,
                                color = Color(0xFF26A69A),
                                style = Stroke(width = strokeWidth)
                            )
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0F2F1),
                        contentColor = Color(0xFF00695C)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "View Timetable Change",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
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
                            val fileUriCopy = attachedFileUri
                            val fileNameCopy = attachedFileName
                            inputValue = ""
                            attachedFileUri = null
                            attachedFileName = null
                            isWaitingForAgent = true
                            
                            // Save user message initially
                            val currentSessionId = sessionId
                            coroutineScope.launch {
                                var idToSave = currentSessionId
                                var isNewSession = false
                                if (idToSave == null) {
                                    isNewSession = true
                                    val newSession = ChatRepository.createChatSession()
                                    idToSave = newSession.id
                                    sessionId = idToSave
                                    ChatStateManager.activeSession = newSession.copy(messages = currentMsgs)
                                } else {
                                    ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(messages = currentMsgs)
                                }
                                ChatRepository.saveChatMessages(idToSave!!, currentMsgs)

                                // Summarize in the background if it's the first message
                                if (isNewSession) {
                                    launch {
                                        val generatedTitle = AgentApi.summarizeChat(userMsg)
                                        ChatRepository.updateChatName(idToSave!!, generatedTitle)
                                        ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(name = generatedTitle)
                                    }
                                }
                                
                                // Show loading text
                                messages = currentMsgs + ChatMessage("assistant", "typing...")
                                
                                // Fetch from Agent
                                val agentResponse = AgentApi.sendMessage(
                                    context = context,
                                    message = userMsg,
                                    history = currentMsgs.dropLast(1), // sending history without the new user msg
                                    fileUri = fileUriCopy,
                                    fileName = fileNameCopy
                                )
                                
                                val newMsgs = currentMsgs + ChatMessage("assistant", agentResponse.response)
                                isWaitingForAgent = false
                                messages = newMsgs
                                ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(messages = newMsgs)
                                ChatRepository.saveChatMessages(idToSave!!, newMsgs)

                                // Trigger redirect button if the AI called show_timetable_button tool
                                if (agentResponse.showButton) {
                                    showRedirectButton = true
                                    delay(10000L) // Show for 10 seconds
                                    showRedirectButton = false
                                }
                            }
                        }
                    },
                    enabled = (inputValue.isNotBlank() || attachedFileUri != null) && !isWaitingForAgent
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = if ((inputValue.isNotBlank() || attachedFileUri != null) && !isWaitingForAgent) colorResource(R.color.uw_gold_lvl4) else Color.Gray
                    )
                }
            }
        }
    }
}
    }

@Composable
fun ChatBubble(message: ChatMessage, primaryYellow: Color) {
    val isAI = message.role == "assistant" || message.role == "agent"
    val isTyping = message.content == "typing..."
    val bubbleColor = if (isAI) Color.Transparent else primaryYellow.copy(alpha = 0.2f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isAI) Alignment.Start else Alignment.End
    ) {
        if (message.attachedFileName != null) {
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Attachment", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = message.attachedFileName, fontSize = 12.sp, color = Color.DarkGray)
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isAI) Arrangement.Start else Arrangement.End
        ) {
            if (isTyping) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryYellow, strokeWidth = 2.dp)
                }
            } else if (isAI) {
                Box(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    colors = CardDefaults.cardColors(containerColor = bubbleColor),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = message.content,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
