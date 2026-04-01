// ProfileScreen.kt
package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFF5F5F5)
    val cardBackground = Color(0xFFFFFFFF)
    val sectionTitleColor = Color(0xFF1A1A1A)
    val labelColor = Color(0xFF444444)
    val fieldBackground = Color(0xFFF0F0F0)

    val auth = remember { FirebaseAuth.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    // ── Profile section state ─────────────────────────────────────────────────
    val displayName by AppState.displayName
    var username by remember(displayName) { mutableStateOf(displayName) }
    var nameSuccess by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var nameLoading by remember { mutableStateOf(false) }
    val email = auth.currentUser?.email ?: ""
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    // ── Password section state ────────────────────────────────────────────────
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var passwordSuccess by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordLoading by remember { mutableStateOf(false) }

    // ── Program & Year Level state ─────────────────────────────────────────────
    var programs by remember { mutableStateOf<List<Program>>(emptyList()) }
    var selectedProgram by remember { mutableStateOf<Program?>(null) }
    var programSearchQuery by remember { mutableStateOf("") }
    var programDropdownExpanded by remember { mutableStateOf(false) }
    val yearLevels = listOf("1A", "1B", "2A", "2B", "3A", "3B", "4A", "4B")
    var selectedYearLevel by remember { mutableStateOf("") }
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var programSaving by remember { mutableStateOf(false) }
    var programSuccess by remember { mutableStateOf<String?>(null) }
    var programError by remember { mutableStateOf<String?>(null) }

    // ── Major state ────────────────────────────────────────────────────────────
    var majors by remember { mutableStateOf<List<Major>>(emptyList()) }
    var selectedMajor by remember { mutableStateOf<Major?>(null) }
    var majorSearchQuery by remember { mutableStateOf("") }
    var majorDropdownExpanded by remember { mutableStateOf(false) }
    var majorSaving by remember { mutableStateOf(false) }
    var majorSuccess by remember { mutableStateOf<String?>(null) }
    var majorError by remember { mutableStateOf<String?>(null) }

    // ── Notifications state ───────────────────────────────────────────────────
    var notifLectureChanges by remember { mutableStateOf(true) }
    var notifNewSections by remember { mutableStateOf(true) }
    var notifConflicts by remember { mutableStateOf(true) }

    // Load saved preferences from Firestore
    LaunchedEffect(Unit) {
        programs = CourseRepository.getPrograms()
        majors = CourseRepository.getMajors()
        CourseRepository.getAdvisors()
        val profile = CourseRepository.getUserExtendedProfile()
        profile["notifLectureChanges"]?.let { notifLectureChanges = it as Boolean }
        profile["notifNewSections"]?.let { notifNewSections = it as Boolean }
        profile["notifConflicts"]?.let { notifConflicts = it as Boolean }
        val programSlug = profile["program"] as? String
        if (programSlug != null) {
            selectedProgram = programs.find { it.slug == programSlug }
        }
        val majorSlug = profile["major"] as? String
        val majorName = profile["majorName"] as? String
        if (majorName != null) {
            selectedMajor = majors.find { it.name == majorName }
        } else if (majorSlug != null) {
            selectedMajor = majors.find { it.slug == majorSlug }
        }
        val yearLevel = profile["yearLevel"] as? Int
        val yearLabel = profile["yearLevelLabel"] as? String
        if (yearLabel != null) {
            selectedYearLevel = yearLabel
        } else if (yearLevel != null) {
            selectedYearLevel = yearLevels.getOrElse((yearLevel - 1) * 2) { "" }
        }
    }

    // Save notification preferences whenever they change
    LaunchedEffect(notifLectureChanges, notifNewSections, notifConflicts) {
        CourseRepository.saveUserExtendedProfile(
            mapOf(
                "notifLectureChanges" to notifLectureChanges,
                "notifNewSections" to notifNewSections,
                "notifConflicts" to notifConflicts
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBackground)
            .statusBarsPadding()
    ) {
        // ── Top header bar (matches other screens) ────────────────────────────
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
                    .clickable { onBack() }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(primaryYellow),
                    contentAlignment = Alignment.Center
                ) {
                    if (nameInitial != null) {
                        Text(nameInitial, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(displayName.ifBlank { "User" }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Return", fontSize = 13.sp, color = Color.Gray)
                }
            }
            TextButton(onClick = onLogout) {
                Text("Logout", color = Color.Gray, fontSize = 14.sp)
            }
        }

        // ── Scrollable body ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {

            // Page title
            Text(
                text = "User Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = sectionTitleColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage your account settings and preferences",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 1. Profile Information ────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Person, title = "Profile Information")
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    FieldLabel("Username")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            nameSuccess = null
                            nameError = null
                        },
                        placeholder = { Text("Your name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !nameLoading,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = fieldBackground,
                            focusedContainerColor = fieldBackground,
                            focusedBorderColor = primaryYellow,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = primaryYellow
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FieldLabel("Email")
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledContainerColor = fieldBackground,
                            disabledBorderColor = Color.Transparent,
                            disabledTextColor = Color(0xFF444444)
                        )
                    )

                    nameError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    nameSuccess?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val trimmed = username.trim()
                            if (trimmed.isBlank()) {
                                nameError = "Name cannot be empty"
                                return@Button
                            }
                            nameLoading = true
                            nameError = null
                            nameSuccess = null
                            coroutineScope.launch {
                                try {
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .setDisplayName(trimmed)
                                        .build()
                                    auth.currentUser?.updateProfile(profileUpdates)?.await()
                                    CourseRepository.saveUserProfile(trimmed)
                                    AppState.displayName.value = trimmed
                                    nameSuccess = "Profile updated successfully!"
                                } catch (e: Exception) {
                                    nameError = e.localizedMessage ?: "Failed to update profile"
                                } finally {
                                    nameLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        ),
                        enabled = !nameLoading
                    ) {
                        if (nameLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Text("Update Profile", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 2. Program & Advisor ────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Info, title = "Program & Advisor")
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    FieldLabel("Program (${programs.size} loaded)")
                    Spacer(modifier = Modifier.height(6.dp))

                    // Program search field
                    OutlinedTextField(
                        value = programSearchQuery,
                        onValueChange = {
                            programSearchQuery = it
                            programDropdownExpanded = true
                            programSuccess = null
                        },
                        placeholder = {
                            Text(
                                selectedProgram?.name ?: "Type to search programs...",
                                color = if (selectedProgram != null) Color(0xFF444444) else Color.LightGray
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Toggle programs",
                                modifier = Modifier.clickable {
                                    programDropdownExpanded = !programDropdownExpanded
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    programDropdownExpanded = true
                                }
                            },
                        singleLine = true,
                        enabled = !programSaving,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = fieldBackground,
                            focusedContainerColor = fieldBackground,
                            focusedBorderColor = primaryYellow,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = primaryYellow
                        )
                    )

                    // Program results list — shows when focused, filters as you type
                    if (programDropdownExpanded && programs.isNotEmpty()) {
                        val filtered = if (programSearchQuery.isBlank()) {
                            programs.take(10)
                        } else {
                            programs.filter {
                                it.name.contains(programSearchQuery, ignoreCase = true)
                            }.take(8)
                        }
                        if (filtered.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    filtered.forEach { program ->
                                        Text(
                                            "${program.name} (${program.faculty})",
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedProgram = program
                                                    programSearchQuery = ""
                                                    programDropdownExpanded = false
                                                    programSuccess = null
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        } else if (programSearchQuery.isNotBlank()) {
                            Text(
                                "No programs found for \"$programSearchQuery\"",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Show selected program
                    if (selectedProgram != null && !programDropdownExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Selected: ${selectedProgram!!.name} (${selectedProgram!!.faculty})",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FieldLabel("Year Level")
                    Spacer(modifier = Modifier.height(6.dp))

                    // Year level as clickable chips
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        yearLevels.forEach { year ->
                            val isSelected = selectedYearLevel == year
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) primaryYellow else fieldBackground
                                    )
                                    .clickable {
                                        selectedYearLevel = year
                                        programSuccess = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    year,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color(0xFF666666)
                                )
                            }
                        }
                    }

                    // Show advisor info if program + year selected
                    if (selectedProgram != null && selectedYearLevel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val isFirstYear = selectedYearLevel.startsWith("1")
                        val advisor = CourseRepository.getAdvisorForProgram(
                            selectedProgram!!.slug, isFirstYear, selectedProgram!!.faculty
                        )
                        if (advisor != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(primaryYellow.copy(alpha = 0.12f))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Your Academic Advisor", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(advisor.name, fontSize = 13.sp, color = Color(0xFF444444))
                                    Text(advisor.email, fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    programError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    programSuccess?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (selectedProgram == null) {
                                programError = "Please select a program"
                                return@Button
                            }
                            programSaving = true
                            programError = null
                            programSuccess = null
                            coroutineScope.launch {
                                try {
                                    val yearNum = if (selectedYearLevel.isNotBlank())
                                        selectedYearLevel.first().digitToInt() else 1
                                    CourseRepository.saveUserExtendedProfile(
                                        mapOf(
                                            "program" to selectedProgram!!.slug,
                                            "faculty" to selectedProgram!!.faculty,
                                            "yearLevel" to yearNum,
                                            "yearLevelLabel" to selectedYearLevel
                                        )
                                    )
                                    programSuccess = "Program saved!"
                                } catch (e: Exception) {
                                    programError = e.localizedMessage ?: "Failed to save"
                                } finally {
                                    programSaving = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        ),
                        enabled = !programSaving
                    ) {
                        if (programSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Text("Save Program", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 3. Major ──────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Info, title = "Major (${majors.size} loaded)")
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    FieldLabel("Major")
                    Spacer(modifier = Modifier.height(6.dp))

                    // Major search field
                    OutlinedTextField(
                        value = majorSearchQuery,
                        onValueChange = {
                            majorSearchQuery = it
                            majorDropdownExpanded = true
                            majorSuccess = null
                        },
                        placeholder = {
                            Text(
                                selectedMajor?.name ?: "Type to search majors...",
                                color = if (selectedMajor != null) Color(0xFF444444) else Color.LightGray
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Toggle majors",
                                modifier = Modifier.clickable {
                                    majorDropdownExpanded = !majorDropdownExpanded
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    majorDropdownExpanded = true
                                }
                            },
                        singleLine = true,
                        enabled = !majorSaving,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = fieldBackground,
                            focusedContainerColor = fieldBackground,
                            focusedBorderColor = primaryYellow,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = primaryYellow
                        )
                    )

                    // Major results list — shows when focused, filters as you type
                    if (majorDropdownExpanded && majors.isNotEmpty()) {
                        val filtered = if (majorSearchQuery.isBlank()) {
                            majors.take(10)
                        } else {
                            majors.filter {
                                it.name.contains(majorSearchQuery, ignoreCase = true)
                            }.take(8)
                        }
                        if (filtered.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    filtered.forEach { major ->
                                        Text(
                                            major.name,
                                            fontSize = 14.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedMajor = major
                                                    majorSearchQuery = ""
                                                    majorDropdownExpanded = false
                                                    majorSuccess = null
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        } else if (majorSearchQuery.isNotBlank()) {
                            Text(
                                "No majors found for \"$majorSearchQuery\"",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Show selected major
                    if (selectedMajor != null && !majorDropdownExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Selected: ${selectedMajor!!.name}",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    majorError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    majorSuccess?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (selectedMajor == null) {
                                majorError = "Please select a major"
                                return@Button
                            }
                            majorSaving = true
                            majorError = null
                            majorSuccess = null
                            coroutineScope.launch {
                                try {
                                    CourseRepository.saveUserExtendedProfile(
                                        mapOf(
                                            "major" to selectedMajor!!.slug,
                                            "majorName" to selectedMajor!!.name
                                        )
                                    )
                                    majorSuccess = "Major saved!"
                                } catch (e: Exception) {
                                    majorError = e.localizedMessage ?: "Failed to save"
                                } finally {
                                    majorSaving = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        ),
                        enabled = !majorSaving
                    ) {
                        if (majorSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Text("Save Major", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 4. Change Password ────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Lock, title = "Change Password")
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    FieldLabel("Current Password")
                    Spacer(modifier = Modifier.height(6.dp))
                    PasswordField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; passwordError = null; passwordSuccess = null },
                        placeholder = "Enter current password",
                        visible = currentPasswordVisible,
                        onToggleVisible = { currentPasswordVisible = !currentPasswordVisible },
                        enabled = !passwordLoading,
                        primaryYellow = primaryYellow,
                        fieldBackground = fieldBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FieldLabel("New Password")
                    Spacer(modifier = Modifier.height(6.dp))
                    PasswordField(
                        value = newPassword,
                        onValueChange = { newPassword = it; passwordError = null; passwordSuccess = null },
                        placeholder = "Enter new password",
                        visible = newPasswordVisible,
                        onToggleVisible = { newPasswordVisible = !newPasswordVisible },
                        enabled = !passwordLoading,
                        primaryYellow = primaryYellow,
                        fieldBackground = fieldBackground
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FieldLabel("Confirm New Password")
                    Spacer(modifier = Modifier.height(6.dp))
                    PasswordField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; passwordError = null; passwordSuccess = null },
                        placeholder = "Confirm new password",
                        visible = confirmPasswordVisible,
                        onToggleVisible = { confirmPasswordVisible = !confirmPasswordVisible },
                        enabled = !passwordLoading,
                        primaryYellow = primaryYellow,
                        fieldBackground = fieldBackground
                    )

                    passwordError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    passwordSuccess?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = Color(0xFF2E7D32), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                                passwordError = "Please fill in all password fields"
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                passwordError = "New password must be at least 6 characters"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                passwordError = "New passwords do not match"
                                return@Button
                            }
                            passwordLoading = true
                            passwordError = null
                            passwordSuccess = null
                            coroutineScope.launch {
                                try {
                                    val user = auth.currentUser ?: throw Exception("Not signed in")
                                    val userEmail = user.email ?: throw Exception("No email on account")
                                    val credential = EmailAuthProvider.getCredential(userEmail, currentPassword)
                                    user.reauthenticate(credential).await()
                                    user.updatePassword(newPassword).await()
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    passwordSuccess = "Password changed successfully!"
                                } catch (e: Exception) {
                                    passwordError = when {
                                        e.message?.contains("credential") == true ||
                                                e.message?.contains("password is invalid") == true ->
                                            "Current password is incorrect"
                                        else -> e.localizedMessage ?: "Failed to change password"
                                    }
                                } finally {
                                    passwordLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        ),
                        enabled = !passwordLoading
                    ) {
                        if (passwordLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 5. Notifications ──────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Notifications, title = "Notifications")
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column {
                    NotificationToggleRow(
                        title = "Lecture Time Changes",
                        description = "Get notified when class times are updated",
                        checked = notifLectureChanges,
                        onCheckedChange = { notifLectureChanges = it },
                        primaryYellow = primaryYellow,
                        showDivider = true
                    )
                    NotificationToggleRow(
                        title = "New Sections Available",
                        description = "Be alerted when new course sections open",
                        checked = notifNewSections,
                        onCheckedChange = { notifNewSections = it },
                        primaryYellow = primaryYellow,
                        showDivider = true
                    )
                    NotificationToggleRow(
                        title = "Schedule Conflicts",
                        description = "Immediate alerts when conflicts are detected",
                        checked = notifConflicts,
                        onCheckedChange = { notifConflicts = it },
                        primaryYellow = primaryYellow,
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 4. About ──────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = sectionTitleColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version 1.0.0", fontSize = 14.sp, color = Color.Gray)
                    Text("Graphical Time Planner", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("For technical support, contact ", fontSize = 14.sp, color = Color.Gray)
                        Text(
                            "d4yim@uwaterloo.ca",
                            fontSize = 14.sp,
                            color = primaryYellow,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable helpers ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF333333),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF333333)
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    enabled: Boolean,
    primaryYellow: Color,
    fieldBackground: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.LightGray) },
        trailingIcon = {
            TextButton(onClick = onToggleVisible) {
                Text(if (visible) "Hide" else "Show", color = Color.Gray, fontSize = 13.sp)
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = fieldBackground,
            focusedContainerColor = fieldBackground,
            focusedBorderColor = primaryYellow,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = primaryYellow
        )
    )
}

@Composable
private fun NotificationToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryYellow: Color,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, fontSize = 13.sp, color = Color.Gray)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primaryYellow,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray
                )
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color(0xFFEEEEEE),
                thickness = 1.dp
            )
        }
    }
}