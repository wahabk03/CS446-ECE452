package com.example.graphicaltimeplanner

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import java.util.UUID

data class ChatMessage(
    val role: String = "",
    val content: String = "",
    val attachedFileName: String? = null,
    val thinkingGroupId: String? = null
)

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
    
    LaunchedEffect(ChatStateManager.activeSession?.id) {
        val session = ChatStateManager.activeSession
        if (session != null) {
            ChatStateManager.sessionId = session.id
            if (!ChatStateManager.isWaitingForAgent || ChatStateManager.messages.isEmpty()) {
                ChatStateManager.messages = session.messages
            }
        } else {
            ChatStateManager.sessionId = null
            if (!ChatStateManager.isWaitingForAgent) {
                ChatStateManager.messages = emptyList()
            }
        }

        inputValue = ChatStateManager.getDraftForSession(ChatStateManager.sessionId)
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
                Icon(Icons.Default.History, contentDescription = "Chat History", tint = Color.Gray)
            }
        }

        // Chat Messages Area
        if (ChatStateManager.messages.isEmpty()) {
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
            val baseMessages = if (
                ChatStateManager.isWaitingForAgent &&
                ChatStateManager.messages.lastOrNull()?.content != "typing..."
            ) {
                ChatStateManager.messages + ChatMessage("assistant", "typing...")
            } else {
                ChatStateManager.messages
            }

            val currentSessionId = ChatStateManager.sessionId
            val foldVersion = ChatStateManager.thinkingFoldVersion

            val groupIndexes = remember(baseMessages) {
                val map = mutableMapOf<String, MutableList<Int>>()
                baseMessages.forEachIndexed { index, msg ->
                    if (msg.role != "tool_status") return@forEachIndexed
                    val groupKey = msg.thinkingGroupId ?: "legacy_$index"
                    map.getOrPut(groupKey) { mutableListOf() }.add(index)
                }
                map
            }

            val displayMessages = remember(baseMessages, groupIndexes, currentSessionId, foldVersion) {
                buildList {
                    baseMessages.forEachIndexed { index, msg ->
                        if (msg.role != "tool_status") {
                            add(msg)
                            return@forEachIndexed
                        }

                        val groupKey = msg.thinkingGroupId ?: "legacy_$index"
                        val indexes = groupIndexes[groupKey] ?: listOf(index)
                        val firstIndex = indexes.first()
                        val lastIndex = indexes.last()
                        val hiddenThinkingSteps = (indexes.size - 1).coerceAtLeast(0)
                        val shouldFoldThinking = indexes.size > 1
                        val isExpanded = ChatStateManager.isThinkingGroupExpanded(currentSessionId, groupKey)

                        if (isExpanded) {
                            if (shouldFoldThinking && index == firstIndex) {
                                add(
                                    ChatMessage(
                                        role = "tool_fold_control",
                                        content = "Hide thinking process",
                                        thinkingGroupId = groupKey
                                    )
                                )
                            }
                            add(msg)
                        } else {
                            if (index == lastIndex) {
                                if (shouldFoldThinking) {
                                    add(
                                        ChatMessage(
                                            role = "tool_fold_control",
                                            content = "Show $hiddenThinkingSteps previous thinking steps",
                                            thinkingGroupId = groupKey
                                        )
                                    )
                                }
                                add(msg)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                items(displayMessages.reversed()) { msg ->
                    if (msg.role == "tool_fold_control") {
                        val groupKey = msg.thinkingGroupId ?: return@items
                        ThinkingFoldControl(
                            label = msg.content,
                            isExpanded = ChatStateManager.isThinkingGroupExpanded(currentSessionId, groupKey),
                            onClick = {
                                val current = ChatStateManager.isThinkingGroupExpanded(currentSessionId, groupKey)
                                ChatStateManager.setThinkingGroupExpanded(currentSessionId, groupKey, !current)
                            }
                        )
                    } else {
                        ChatBubble(message = msg, primaryYellow = primaryYellow)
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

        // Action popup button
        if (ChatStateManager.showRedirectButton) {
            LaunchedEffect(ChatStateManager.showRedirectButton) {
                if (ChatStateManager.showRedirectButton) {
                    val totalSteps = 100
                    for (step in totalSteps downTo 0) {
                        if (!ChatStateManager.showRedirectButton) break
                        ChatStateManager.redirectButtonCountdownProgress = step / totalSteps.toFloat()
                        delay(80)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Button(
                        onClick = {
                            ChatStateManager.showRedirectButton = false
                            onNavigateToTimetable()
                        },
                        modifier = Modifier
                            .matchParentSize(),
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

                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                    ) {
                        val progress = ChatStateManager.redirectButtonCountdownProgress.coerceIn(0f, 1f)
                        val strokePx = 2.5.dp.toPx()
                        val inset = strokePx / 2f
                        val rr = RoundRect(
                            left = inset,
                            top = inset,
                            right = size.width - inset,
                            bottom = size.height - inset,
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )

                        val borderPath = Path().apply { addRoundRect(rr) }
                        val pathMeasure = PathMeasure().apply { setPath(borderPath, false) }
                        val countdownPath = Path()
                        pathMeasure.getSegment(0f, pathMeasure.length * progress, countdownPath, true)

                        drawPath(
                            path = countdownPath,
                            color = Color(0xFF26A69A),
                            style = Stroke(width = strokePx)
                        )
                    }
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
                    onValueChange = {
                        inputValue = it
                        ChatStateManager.setDraftForSession(ChatStateManager.sessionId, it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the agent...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                IconButton(
                    onClick = {
                        if (inputValue.isNotBlank() || attachedFileUri != null) {
                            // Add user message
                            val currentMsgs = ChatStateManager.messages + ChatMessage("user", inputValue, attachedFileName)
                            ChatStateManager.messages = currentMsgs
                            val userMsg = inputValue
                            val fileUriCopy = attachedFileUri
                            val fileNameCopy = attachedFileName
                            val currentDraftSessionId = ChatStateManager.sessionId
                            ChatStateManager.clearDraftForSession(currentDraftSessionId)
                            inputValue = ""
                            attachedFileUri = null
                            attachedFileName = null
                            ChatStateManager.isWaitingForAgent = true
                            
                            // Save user message initially
                            val currentSessionId = ChatStateManager.sessionId
                            ChatStateManager.appScope.launch {
                                var idToSave = currentSessionId
                                var isNewSession = false
                                if (idToSave == null) {
                                    isNewSession = true
                                    val newSession = ChatRepository.createChatSession()
                                    idToSave = newSession.id
                                    ChatStateManager.sessionId = idToSave
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
                                
                                // Fetch from Agent
                                val thinkingGroupId = UUID.randomUUID().toString()
                                ChatStateManager.setThinkingGroupExpanded(idToSave, thinkingGroupId, false)

                                val agentResponse = AgentApi.sendMessage(
                                    context = context,
                                    message = userMsg,
                                    history = currentMsgs.dropLast(1), // sending history without the new user msg
                                    fileUri = fileUriCopy,
                                    fileName = fileNameCopy,
                                    onToolEvent = { toolMessage ->
                                        ChatStateManager.appScope.launch {
                                            val progressMsgs = ChatStateManager.messages + ChatMessage(
                                                role = "tool_status",
                                                content = toolMessage,
                                                thinkingGroupId = thinkingGroupId
                                            )
                                            ChatStateManager.messages = progressMsgs
                                            ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(messages = progressMsgs)
                                        }
                                    }
                                )

                                val newMsgs = ChatStateManager.messages + ChatMessage("assistant", agentResponse.response)
                                ChatStateManager.isWaitingForAgent = false
                                ChatStateManager.messages = newMsgs
                                ChatStateManager.activeSession = ChatStateManager.activeSession?.copy(messages = newMsgs)
                                ChatRepository.saveChatMessages(idToSave!!, newMsgs)

                                // Trigger redirect button if the AI called show_timetable_button tool
                                if (agentResponse.showButton) {
                                    ChatStateManager.showRedirectButton = true
                                    ChatStateManager.redirectButtonCountdownProgress = 1f
                                }
                            }
                        }
                    },
                    enabled = (inputValue.isNotBlank() || attachedFileUri != null) && !ChatStateManager.isWaitingForAgent
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if ((inputValue.isNotBlank() || attachedFileUri != null) && !ChatStateManager.isWaitingForAgent) colorResource(R.color.uw_gold_lvl4) else Color.Gray
                    )
                }
            }
        }
    }
}
    }

@Composable
fun ChatBubble(message: ChatMessage, primaryYellow: Color) {
    val isToolStatus = message.role == "tool_status"
    val isAI = message.role == "assistant" || message.role == "agent" || isToolStatus
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
            } else if (isToolStatus) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    ToolStatusMessageText(message = message.content)
                }
            } else if (isAI) {
                Box(modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                    MarkdownMessageText(message.content)
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

@Composable
private fun ThinkingFoldControl(
    label: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .clickable(onClick = onClick),
        color = Color(0xFFF4F6F7),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse thinking process" else "Expand thinking process",
                tint = Color(0xFF6E6E6E),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFF5F6368)
            )
        }
    }
}

@Composable
private fun ToolStatusMessageText(message: String) {
    Text(
        text = buildAnnotatedString { appendToolStatusMessage(message) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color(0xFF616161),
        fontSize = 13.sp
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendToolStatusMessage(source: String) {
    var i = 0
    while (i < source.length) {
        if (source[i] == '"') {
            val end = source.indexOf('"', startIndex = i + 1)
            if (end != -1) {
                val quoted = source.substring(i, end + 1)
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                append(quoted)
                pop()
                i = end + 1
                continue
            }
        }
        append(source[i])
        i++
    }
}

@Composable
private fun MarkdownMessageText(markdown: String) {
    val blocks = remember(markdown) { markdown.split("```") }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { idx, block ->
            if (block.isBlank()) return@forEachIndexed

            if (idx % 2 == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8F8F8), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = block.trimEnd(),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF333333)
                    )
                }
            } else {
                block.lines().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    when {
                        line.startsWith("### ") -> Text(
                            text = line.removePrefix("### "),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        line.startsWith("## ") -> Text(
                            text = line.removePrefix("## "),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        line.startsWith("# ") -> Text(
                            text = line.removePrefix("# "),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        line.startsWith("- ") -> Text(
                            text = buildAnnotatedString {
                                append("• ")
                                appendInlineMarkdown(line.removePrefix("- "))
                            },
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                        else -> Text(
                            text = buildAnnotatedString { appendInlineMarkdown(line) },
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(source: String) {
    var i = 0
    var bold = false
    var code = false
    val buf = StringBuilder()

    fun flushBuffer() {
        if (buf.isEmpty()) return
        val text = buf.toString()
        buf.clear()
        pushStyle(
            SpanStyle(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
                background = if (code) Color(0xFFF0F0F0) else Color.Transparent
            )
        )
        append(text)
        pop()
    }

    while (i < source.length) {
        val nextTwo = if (i + 1 < source.length) source.substring(i, i + 2) else ""
        when {
            nextTwo == "**" -> {
                flushBuffer()
                bold = !bold
                i += 2
            }
            source[i] == '`' -> {
                flushBuffer()
                code = !code
                i += 1
            }
            else -> {
                buf.append(source[i])
                i += 1
            }
        }
    }
    flushBuffer()
}
