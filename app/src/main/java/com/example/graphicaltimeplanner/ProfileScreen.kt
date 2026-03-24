package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser

    var programs by remember { mutableStateOf<List<Program>>(emptyList()) }
    var selectedProgram by remember { mutableStateOf<Program?>(null) }
    var programSearchQuery by remember { mutableStateOf("") }
    var programDropdownExpanded by remember { mutableStateOf(false) }

    val yearLevels = listOf("1A", "1B", "2A", "2B", "3A", "3B", "4A", "4B")
    var selectedYearLevel by remember { mutableStateOf("") }
    var yearDropdownExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load programs and user profile
    LaunchedEffect(Unit) {
        programs = CourseRepository.getPrograms()
        CourseRepository.getAdvisors() // Pre-cache advisors
        val (programSlug, _, yearLevel) = CourseRepository.getUserProfile()
        if (programSlug != null) {
            selectedProgram = programs.find { it.slug == programSlug }
        }
        if (yearLevel != null) {
            // Find the year level label (e.g., "2A") from the number
            selectedYearLevel = yearLevels.getOrElse((yearLevel - 1) * 2) { "" }
            // Try to load the exact label from Firestore
            val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user?.uid ?: "").get().await()
            val label = doc.getString("yearLevelLabel")
            if (label != null) selectedYearLevel = label
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f))
    ) {
        // Top banner with back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colorResource(R.color.uw_gold_lvl4))
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
            Text(
                text = "Profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorResource(R.color.uw_gold_lvl4))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Email (read-only)
                        OutlinedTextField(
                            value = user?.email ?: "",
                            onValueChange = {},
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = Color.Gray,
                                disabledLabelColor = Color.Gray,
                                disabledTextColor = Color.Black
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Program selection
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (programDropdownExpanded) programSearchQuery
                                        else selectedProgram?.name ?: "",
                                onValueChange = {
                                    programSearchQuery = it
                                    programDropdownExpanded = true
                                },
                                label = { Text("Program") },
                                placeholder = { Text("Search your program...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isSaving,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                                    focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                                    cursorColor = colorResource(R.color.uw_gold_lvl4)
                                )
                            )
                            DropdownMenu(
                                expanded = programDropdownExpanded && programs.isNotEmpty(),
                                onDismissRequest = { programDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                val filtered = programs.filter {
                                    it.name.contains(programSearchQuery, ignoreCase = true)
                                }
                                filtered.forEach { program ->
                                    DropdownMenuItem(
                                        text = { Text(program.name, fontSize = 14.sp) },
                                        onClick = {
                                            selectedProgram = program
                                            programSearchQuery = ""
                                            programDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Year level selection
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedYearLevel,
                                onValueChange = {},
                                label = { Text("Year Level") },
                                placeholder = { Text("Select year...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { yearDropdownExpanded = true },
                                singleLine = true,
                                readOnly = true,
                                enabled = !isSaving,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colorResource(R.color.uw_gold_lvl4),
                                    focusedLabelColor = colorResource(R.color.uw_gold_lvl4),
                                    cursorColor = colorResource(R.color.uw_gold_lvl4)
                                )
                            )
                            DropdownMenu(
                                expanded = yearDropdownExpanded,
                                onDismissRequest = { yearDropdownExpanded = false }
                            ) {
                                yearLevels.forEach { year ->
                                    DropdownMenuItem(
                                        text = { Text(year) },
                                        onClick = {
                                            selectedYearLevel = year
                                            yearDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Show current advisor info
                        if (selectedProgram != null && selectedYearLevel.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            val isFirstYear = selectedYearLevel.startsWith("1")
                            val advisor = CourseRepository.getAdvisorForProgram(
                                selectedProgram!!.slug, isFirstYear, selectedProgram!!.faculty
                            )
                            if (advisor != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Your Academic Advisor",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(advisor.name, fontSize = 13.sp)
                                        Text(advisor.email, fontSize = 13.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (selectedProgram == null) {
                                    saveMessage = "Please select a program"
                                    return@Button
                                }
                                isSaving = true
                                saveMessage = null
                                coroutineScope.launch {
                                    val yearNum = if (selectedYearLevel.isNotBlank())
                                        selectedYearLevel.first().digitToInt() else 1
                                    CourseRepository.saveUserProfile(
                                        selectedProgram!!.slug,
                                        selectedProgram!!.faculty,
                                        yearNum
                                    )
                                    // Also save the label
                                    if (selectedYearLevel.isNotBlank()) {
                                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                                        if (uid != null) {
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                .collection("users").document(uid)
                                                .update("yearLevelLabel", selectedYearLevel)
                                                .await()
                                        }
                                    }
                                    isSaving = false
                                    saveMessage = "Profile saved!"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.uw_gold_lvl4),
                                contentColor = Color.Black
                            ),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.Black,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Text("SAVE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (saveMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                saveMessage!!,
                                color = if (saveMessage == "Profile saved!") Color(0xFF2E7D32) else Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}
