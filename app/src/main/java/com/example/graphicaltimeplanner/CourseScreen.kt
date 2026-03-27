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

    // Clear expanded state when the subject or term changes so stale cards
    // don't stay open after a fresh load.
    LaunchedEffect(selectedSubject, selectedTerm) {
        expandedCourses.clear()
        isLoading = true
        errorMessage = null
        try {
            courses = CourseRepository.getCourses(term = selectedTerm, subject = selectedSubject)
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
                                onAddSection = { section ->
                                    val newCourse = Course(
                                        code = group.code,
                                        title = group.title,
                                        section = section,
                                        term = selectedTerm,
                                        units = group.units
                                    )
                                    // Enforce one section per component type (LEC, TUT, LAB, etc.)
                                    // within the same course: remove any existing section of the
                                    // same type before adding the new one.
                                    AppState.scheduledCourses.removeAll {
                                        it.code == newCourse.code &&
                                                it.section.componentType == newCourse.section.componentType
                                    }
                                    AppState.scheduledCourses.add(newCourse)
                                    // Persist — keep add and remove paths consistent
                                    scope.launch {
                                        CourseRepository.saveUserSchedule(AppState.scheduledCourses)
                                    }
                                },
                                onRemoveSection = { section ->
                                    val toRemove = AppState.scheduledCourses.find {
                                        it.code == group.code &&
                                                it.section.classNumber == section.classNumber
                                    }
                                    if (toRemove != null) {
                                        AppState.scheduledCourses.remove(toRemove)
                                        scope.launch {
                                            CourseRepository.saveUserSchedule(AppState.scheduledCourses)
                                        }
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
    onAddSection: (Section) -> Unit,
    onRemoveSection: (Section) -> Unit   // caller owns remove + save
) {
    val primaryYellow = Color(0xFFFFD700)

    // Which distinct component types (LEC, TUT, LAB …) of this course are scheduled
    val addedTypes = group.sections
        .filter { course ->
            scheduledCourses.any {
                it.code == group.code && it.section.classNumber == course.section.classNumber
            }
        }
        .map { it.section.componentType }
        .distinct()
        .sorted()

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

                if (addedTypes.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        addedTypes.forEach { type ->
                            Box(
                                modifier = Modifier
                                    .background(primaryYellow, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
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
                        // This exact section is currently scheduled
                        val isAdded = scheduledCourses.any {
                            it.code == group.code && it.section.classNumber == section.classNumber
                        }
                        // A *different* section of the same component type is scheduled
                        // (e.g. user already has LEC 001, now looking at LEC 002)
                        val isConflicting = !isAdded && scheduledCourses.any {
                            it.code == group.code &&
                                    it.section.componentType == section.componentType
                        }

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

                            when {
                                isAdded -> {
                                    // Currently selected — tap to deselect
                                    Button(
                                        onClick = { onRemoveSection(section) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = primaryYellow.copy(alpha = 0.15f),
                                            contentColor = primaryYellow
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("✓ Added", fontSize = 14.sp)
                                    }
                                }
                                isConflicting -> {
                                    // Different section of same type already chosen — offer swap
                                    Button(
                                        onClick = { onAddSection(section) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color(0xFFFF8C00)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFFF8C00)),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("↔ Swap", fontSize = 14.sp)
                                    }
                                }
                                else -> {
                                    // Not added yet — plain add
                                    Button(
                                        onClick = { onAddSection(section) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = primaryYellow
                                        ),
                                        border = BorderStroke(1.dp, primaryYellow),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("+ Add", fontSize = 14.sp)
                                    }
                                }
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