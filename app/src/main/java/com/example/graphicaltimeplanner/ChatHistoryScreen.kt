package com.example.graphicaltimeplanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var chatHistories by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        chatHistories = ChatRepository.getChatSessions()
        isLoading = false
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF5FBFA),
            Color(0xFFFFFFFF)
        )
    )
    val accentTeal = Color(0xFF00695C)
    val softTeal = Color(0xFFE0F2F1)
    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, softTeal),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = accentTeal
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back", fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "Chat History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B)
            )

            Spacer(modifier = Modifier.width(86.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Recent Conversations",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Text(
                        text = "Reopen a previous chat or start fresh",
                        fontSize = 13.sp,
                        color = Color(0xFF6E6E6E)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        ChatStateManager.activeSession = null
                        onBack()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFFE082),
                        contentColor = Color(0xFF1F1F1F)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentTeal)
            }
        } else if (chatHistories.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(softTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = accentTeal)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No chat history yet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = Color(0xFF2A2A2A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start a new chat with the assistant and your sessions will appear here.",
                        color = Color(0xFF7A7A7A),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatHistories) { session ->
                    val createdAtText = remember(session.createdAt) {
                        runCatching { dateFormat.format(session.createdAt.toDate()) }
                            .getOrElse { "Unknown date" }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ChatStateManager.activeSession = session
                                onBack()
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Column {
                                    Text(
                                        text = session.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF212121),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = createdAtText,
                                        fontSize = 12.sp,
                                        color = Color(0xFF6F6F6F)
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        ChatRepository.deleteChatSession(session.id)
                                        chatHistories = ChatRepository.getChatSessions()
                                        if (ChatStateManager.activeSession?.id == session.id) {
                                            ChatStateManager.activeSession = null
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}