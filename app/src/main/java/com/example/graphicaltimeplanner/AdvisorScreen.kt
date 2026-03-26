// AdvisorScreen.kt
package com.example.graphicaltimeplanner

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─── Data ─────────────────────────────────────────────────────────────────────

private data class MajorLink(val name: String, val url: String)

private val MAJOR_LINKS = listOf(
    MajorLink("Arts",        "https://uwaterloo.ca/arts/undergraduate/student-support/academic-advisors"),
    MajorLink("Engineering", "https://uwaterloo.ca/engineering/undergraduate-students/getting-support/academic-advisors"),
    MajorLink("Environment", "https://uwaterloo.ca/environment/undergraduate/current-students/env-academic-advising"),
    MajorLink("Health",      "https://uwaterloo.ca/health/current-undergraduates/advisors"),
    MajorLink("Mathematics", "https://uwaterloo.ca/math/undergraduate-studies/student-experience-and-supports/academic-advising"),
    MajorLink("Science",     "https://uwaterloo.ca/science-undergraduate-office/student-support/academic-advisors"),
)

private const val GENERAL_ADVISING_URL =
    "https://uwaterloo.ca/the-centre/academics/academics-undergraduate-students/academic-advisors"

// ─── Email template (no network call needed) ──────────────────────────────────

data class GeneratedEmail(
    val subject: String,
    val body: String
)

private fun buildEmailFromTemplate(issue: String, userName: String): GeneratedEmail {
    val name = userName.ifBlank { "[Your Name]" }

    // Build schedule summary from AppState
    val scheduleSummary = buildString {
        val courses = AppState.scheduledCourses
        if (courses.isNotEmpty()) {
            appendLine()
            appendLine("For reference, my current schedule includes:")
            val grouped = courses.groupBy { it.code }
            for ((code, sections) in grouped.toSortedMap()) {
                val title = sections.first().title
                appendLine("  - $code ($title)")
            }
        }
    }

    val body = """Dear Academic Advisor,

I hope this email finds you well. I am writing to seek your guidance regarding an academic matter.

$issue
$scheduleSummary
I would greatly appreciate your assistance and advice on how to proceed with this situation. Please let me know if you need any additional information from me.
I am available for a meeting at your convenience, either in person or virtually.

Thank you for your time and support.

Best regards,
$name"""
    return GeneratedEmail(
        subject = "Request for Academic Advising Assistance",
        body = body
    )
}

// ─── AdvisorScreen ────────────────────────────────────────────────────────────

@Composable
fun AdvisorScreen(
    onViewProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToChatbot: () -> Unit = {},
    onNavigateToAi: () -> Unit = {}
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFF5F5F5)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val displayName by AppState.displayName
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    // Email dialog state
    var showComposeDialog by remember { mutableStateOf(false) }
    var issueText by remember { mutableStateOf("") }
    var generatedEmail by remember { mutableStateOf<GeneratedEmail?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    // Editable fields in the review dialog
    var editFromEmail by remember { mutableStateOf("student@uwaterloo.ca") }
    var editToEmail by remember { mutableStateOf("advisor@uwaterloo.ca") }
    var editSubject by remember { mutableStateOf("") }
    var editBody by remember { mutableStateOf("") }

    // Load advisor email from Firestore based on user's program
    LaunchedEffect(Unit) {
        val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
        if (!userEmail.isNullOrBlank()) editFromEmail = userEmail

        // Load advisors and user profile to auto-fill the "To" email
        CourseRepository.getAdvisors()
        val profile = CourseRepository.getUserExtendedProfile()
        val programSlug = profile["program"] as? String
        val faculty = profile["faculty"] as? String
        val yearLevel = profile["yearLevel"] as? Int
        if (programSlug != null && faculty != null) {
            val isFirstYear = (yearLevel ?: 1) <= 1
            val advisor = CourseRepository.getAdvisorForProgram(programSlug, isFirstYear, faculty)
            if (advisor != null) {
                editToEmail = advisor.email
            }
        }
    }

    // URL opener
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ── Compose dialog ────────────────────────────────────────────────────────
    if (showComposeDialog) {
        Dialog(
            onDismissRequest = { showComposeDialog = false; issueText = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Column {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = primaryYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Describe Your Issue",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showComposeDialog = false; issueText = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tell us what you need help with, and our AI will draft a professional email for you.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        "What problem or question do you want to discuss with your advisor?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = issueText,
                        onValueChange = { issueText = it },
                        placeholder = {
                            Text(
                                "Example: I have a scheduling conflict between two required courses for my major. " +
                                        "CS 341 and MATH 239 both meet on Monday and Wednesday at 2:30 PM. I need guidance on how to resolve this...",
                                fontSize = 13.sp,
                                color = Color(0xFFAAAAAA)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF7F7F7),
                            focusedContainerColor = Color(0xFFF7F7F7),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryYellow,
                            cursorColor = primaryYellow
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (issueText.isNotBlank()) {
                                val result = buildEmailFromTemplate(issueText, displayName)
                                generatedEmail = result
                                editSubject = result.subject
                                editBody = result.body
                                showComposeDialog = false
                                issueText = ""
                                showResultDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = issueText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow.copy(alpha = if (issueText.isNotBlank()) 1f else 0.5f),
                            contentColor = Color.Black,
                            disabledContainerColor = primaryYellow.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Email with AI", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // ── Review and Edit dialog ────────────────────────────────────────────────
    if (showResultDialog && generatedEmail != null) {
        Dialog(
            onDismissRequest = { showResultDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = primaryYellow, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Review and Edit Your Email", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showResultDialog = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Review the AI-generated email and make any changes before sending.",
                        fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // From
                    Text("From (Your Email)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editFromEmail,
                        onValueChange = { editFromEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor = Color(0xFFF2F2F7),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryYellow,
                            cursorColor = primaryYellow
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // To
                    Text("To (Advisor Email)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editToEmail,
                        onValueChange = { editToEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor = Color(0xFFF2F2F7),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryYellow,
                            cursorColor = primaryYellow
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Subject
                    Text("Subject", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor = Color(0xFFF2F2F7),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryYellow,
                            cursorColor = primaryYellow
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Body
                    Text("Email Content", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF2F2F7),
                            focusedContainerColor = Color(0xFFF2F2F7),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = primaryYellow,
                            cursorColor = primaryYellow
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showResultDialog = false
                                showComposeDialog = true
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDDDDD))
                        ) {
                            Text("Back", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                // Open mail client with mailto: intent
                                val mailto = "mailto:${Uri.encode(editToEmail)}" +
                                        "?subject=${Uri.encode(editSubject)}" +
                                        "&body=${Uri.encode(editBody)}"
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto))
                                context.startActivity(Intent.createChooser(intent, "Send Email"))
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Email", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // ── Main layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBackground)
            .statusBarsPadding()
    ) {
        // Top header (same style as all other screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
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
            TextButton(onClick = onLogout) {
                Text("Logout", color = Color.Gray, fontSize = 15.sp)
            }
        }

        // Body
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {

            // ── Page title ────────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Academic Advisor", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Connect with advisors and access academic resources",
                    fontSize = 14.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── 1. Email Your Advisor (top section) ───────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Email Your Advisor",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Need personalized help? Our AI assistant can help you draft a professional email to your academic advisor.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showComposeDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Compose Email to Advisor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 2. Academic Resources ─────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.open_in_new_24px),
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Academic Resources",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Access academic advising resources for your program. Visit the general advising page or select your specific faculty below:",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // General advising link
                        AdvisorLinkRow(
                            label = "General Academic Advising",
                            onClick = { openUrl(GENERAL_ADVISING_URL) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Select Your Faculty:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Faculty links
                        MAJOR_LINKS.forEach { major ->
                            Spacer(modifier = Modifier.height(8.dp))
                            AdvisorLinkRow(
                                label = major.name,
                                onClick = { openUrl(major.url) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Bottom nav
        BottomNavBar(
            selectedItem = BottomNavItem.ADVISOR,
            onCoursesClick = onNavigateToCourses,
            onAiClick = onNavigateToAi,
            onScheduleClick = onNavigateToHome,
            onChatbotClick = onNavigateToChatbot,
            onAdvisorClick = {}
        )
    }
}

// ─── Reusable link row ────────────────────────────────────────────────────────

@Composable
private fun AdvisorLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Icon(
            painter = painterResource(R.drawable.open_in_new_24px),
            contentDescription = "Open link",
            tint = Color(0xFF888888),
            modifier = Modifier.size(18.dp)
        )
    }
}
