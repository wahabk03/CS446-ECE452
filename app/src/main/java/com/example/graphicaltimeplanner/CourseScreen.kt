// CourseScreen.kt
package com.example.graphicaltimeplanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CourseScreen(
    onViewProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onBackToHome: () -> Unit = {},
    onNavigateToChatbot: () -> Unit = {},
    onNavigateToAi: () -> Unit = {},
    onNavigateToAdvisor: () -> Unit = {}
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFFDFDFD)

    val displayName by AppState.displayName
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    var courses by remember { mutableStateOf(listOf<Course>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("CS") }
    var missingComponents by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMissingComponentsDialog by remember { mutableStateOf(false) }
    // Derive the term directly from the active timetable so CourseScreen
    // always browses the same term that was chosen in HomeScreen.
    // Use derivedStateOf so this re-derives whenever *either* activeTimetableId
    // or the timetables list mutates — remember(activeTimetableId){} misses
    // the case where timetables load after the first composition.
    val activeTimetableId by AppState.activeTimetableId
    val selectedTerm by remember {
        derivedStateOf {
            AppState.timetables.find { it.id == activeTimetableId }?.term
                ?.takeIf { it.isNotBlank() } ?: "1261"
        }
    }
    val selectedTermLabel by remember {
        derivedStateOf {
            CourseRepository.TERM_MAPPINGS
                .find { it.first == selectedTerm }?.second ?: ""
        }
    }

    // Track expanded course codes
    val expandedCourses = remember { mutableStateSetOf<String>() }

    // Observe the SnapshotStateList directly so Compose recomposes on every
    // add / remove.  rememberUpdatedState(.toList()) only captures a snapshot
    // at composition time and misses subsequent mutations.
    val scheduledCourses = AppState.scheduledCourses

    val scope = rememberCoroutineScope()

    // Let typed search override the subject tab when a subject prefix is present
    // (e.g. "STAT 333" fetches STAT even if the active tab is CS).
    val subjectFromQuery = remember(searchQuery) {
        val query = searchQuery.trim().uppercase()
        if (query.isBlank()) {
            null
        } else {
            val prefix = Regex("^([A-Z]+)").find(query)?.groupValues?.getOrNull(1)
            prefix?.takeIf { it in CourseRepository.ALL_SUBJECTS }
        }
    }
    val fetchSubject = subjectFromQuery ?: selectedSubject

    // Clear expanded state when the subject or term changes so stale cards
    // don't stay open after a fresh load.
    LaunchedEffect(fetchSubject, selectedTerm) {
        expandedCourses.clear()
        isLoading = true
        errorMessage = null
        try {
            courses = CourseRepository.getCourses(term = selectedTerm, subject = fetchSubject)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load courses"
            courses = emptyList()
        } finally {
            isLoading = false
        }
    }

    // Group sections by course code
    val groupedCourses = remember(courses) {
        courses
            .groupBy { it.code }
            .map { (code, sections) ->
                val first = sections.firstOrNull() ?: return@map null
                CourseGroup(
                    code = code,
                    title = first.title,
                    units = first.units,
                    sections = sections
                )
            }
            .filterNotNull()
    }

    val filteredGroups = remember(groupedCourses, searchQuery) {
        if (searchQuery.isBlank()) groupedCourses
        else {
            val q = searchQuery.lowercase()
            groupedCourses.filter {
                it.code.lowercase().contains(q) || it.title.lowercase().contains(q)
            }
        }
    }

    if (showMissingComponentsDialog) {
        AlertDialog(
            onDismissRequest = { showMissingComponentsDialog = false },
            title = { Text("Missing Required Sessions") },
            text = {
                val missing = missingComponents.joinToString(", ")
                Text(
                    "Please select all required session types before confirming this course: $missing."
                )
            },
            confirmButton = {
                TextButton(onClick = { showMissingComponentsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = lightBackground,
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.COURSES,
                onCoursesClick = {},
                onAiClick = onNavigateToAi,
                onScheduleClick = onBackToHome,
                onChatbotClick = onNavigateToChatbot,
                onAdvisorClick = onNavigateToAdvisor
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(lightBackground)
        ) {
            // Top user header
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

                TextButton(onClick = onLogout) {
                    Text(
                        text = "Logout",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search courses...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryYellow,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Active term badge — read-only, driven by the active timetable
            if (selectedTermLabel.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Showing courses for:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Box(
                        modifier = Modifier
                            .background(primaryYellow, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedTermLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subject chips — hardcoded from ALL_SUBJECTS, no network fetch needed
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CourseRepository.ALL_SUBJECTS) { subject ->
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { selectedSubject = subject },
                        label = { Text(subject) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryYellow,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White,
                            labelColor = Color.Black
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selectedSubject == subject) Color.Transparent else Color.LightGray,
                            enabled = true,
                            selected = selectedSubject == subject
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryYellow)
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                    }
                }
                filteredGroups.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No courses found",
                            fontSize = 18.sp,
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(filteredGroups) { group ->
                            ExpandableCourseGroup(
                                group = group,
                                scheduledCourses = scheduledCourses,
                                isExpanded = expandedCourses.contains(group.code),
                                onToggleExpand = {
                                    if (expandedCourses.contains(group.code)) {
                                        expandedCourses.remove(group.code)
                                    } else {
                                        expandedCourses.add(group.code)
                                    }
                                },
                                onMissingRequired = { missing ->
                                    missingComponents = missing
                                    showMissingComponentsDialog = true
                                },
                                onConfirmSections = { confirmedSections ->
                                    val newCourses = confirmedSections.map { section ->
                                        Course(
                                            code = group.code,
                                            title = group.title,
                                            section = section,
                                            term = selectedTerm,
                                            units = group.units
                                        )
                                    }

                                    // Replace this course's existing sections in one shot.
                                    AppState.scheduledCourses.removeAll { it.code == group.code }
                                    AppState.scheduledCourses.addAll(newCourses)

                                    scope.launch {
                                        val activeId = AppState.activeTimetableId.value
                                        if (activeId != null) {
                                            val idx = AppState.timetables.indexOfFirst { it.id == activeId }
                                            if (idx >= 0) {
                                                AppState.timetables[idx] = AppState.timetables[idx].copy(
                                                    courses = AppState.scheduledCourses.toList()
                                                )
                                            }
                                        }
                                        CourseRepository.saveAllTimetables(
                                            AppState.timetables.toList(),
                                            AppState.activeTimetableId.value
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class CourseGroup(
    val code: String,
    val title: String,
    val units: String,
    val sections: List<Course>
)

@Composable
fun ExpandableCourseGroup(
    group: CourseGroup,
    scheduledCourses: List<Course>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMissingRequired: (List<String>) -> Unit,
    onConfirmSections: (List<Section>) -> Unit
) {
    val primaryYellow = Color(0xFFFFD700)

    val committedByType = remember(scheduledCourses, group.code) {
        scheduledCourses
            .filter { it.code == group.code }
            .associateBy { it.section.componentType }
            .mapValues { it.value.section }
    }

    var pendingByType by remember(group.code) {
        mutableStateOf<Map<String, Section>>(emptyMap())
    }
    var lastConfirmedByType by remember(group.code) {
        mutableStateOf<Map<String, Section>>(emptyMap())
    }

    LaunchedEffect(committedByType, group.code) {
        pendingByType = committedByType
        lastConfirmedByType = committedByType
    }

    val requiredTypes = remember(group.sections) {
        group.sections
            .map { it.section.componentType }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val selectedTypes = remember(pendingByType) {
        pendingByType.keys.sorted()
    }

    val hasPendingChanges = remember(lastConfirmedByType, pendingByType) {
        lastConfirmedByType != pendingByType
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.code,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = group.title,
                        fontSize = 14.sp,
                        color = Color(0xFF555555)
                    )
                    Text(
                        text = "${group.units} credits",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                Column(
                    modifier = Modifier
                        .width(90.dp)
                        .height(64.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasPendingChanges) {
                            Button(
                                onClick = {
                                    val missing = requiredTypes.filter { !pendingByType.containsKey(it) }
                                    if (missing.isNotEmpty()) {
                                        onMissingRequired(missing)
                                    } else {
                                        // Clear pending state immediately for responsive UX.
                                        lastConfirmedByType = pendingByType
                                        onConfirmSections(pendingByType.values.toList())
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryYellow,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            selectedTypes.forEach { type ->
                                Box(
                                    modifier = Modifier
                                        .background(primaryYellow, RoundedCornerShape(7.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = type,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 90f else 0f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(color = Color(0xFFEEEEEE))

                    group.sections.forEachIndexed { index, course ->
                        val section = course.section
                        val selectedForType = pendingByType[section.componentType]
                        val isSelected = selectedForType?.classNumber == section.classNumber
                        val isSwap = selectedForType != null && !isSelected

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // e.g. "LEC 001" — full component string
                                    Text(
                                        text = section.component,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }

                                // Show "TBA" for online/TBA sections that have no
                                // parseable schedule (empty days + midnight times).
                                val scheduleText = if (section.days.isEmpty()) {
                                    "TBA"
                                } else {
                                    section.days.joinToString(", ") +
                                            " ${section.startTime}-${section.endTime}"
                                }
                                Text(
                                    text = scheduleText,
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666)
                                )
                            }

                            Button(
                                onClick = {
                                    pendingByType = pendingByType.toMutableMap().apply {
                                        this[section.componentType] = section
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isSelected -> primaryYellow.copy(alpha = 0.18f)
                                        else -> Color.White
                                    },
                                    contentColor = when {
                                        isSelected -> primaryYellow
                                        isSwap -> Color(0xFFFF8C00)
                                        else -> primaryYellow
                                    }
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> primaryYellow
                                        isSwap -> Color(0xFFFF8C00)
                                        else -> primaryYellow
                                    }
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    when {
                                        isSelected -> "Selected"
                                        isSwap -> "Swap"
                                        else -> "Select"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (index < group.sections.size - 1) {
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                        }
                    }
                }
            }
        }
    }
}