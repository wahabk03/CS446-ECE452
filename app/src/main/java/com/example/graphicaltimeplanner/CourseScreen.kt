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
    onNavigateToChatbot: () -> Unit = {}
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFFDFDFD)

    var courses by remember { mutableStateOf(listOf<Course>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("CS") }

    // Track expanded course codes
    val expandedCourses = remember { mutableStateSetOf<String>() }

    // Reactive state from AppState
    val scheduledCourses by rememberUpdatedState(AppState.scheduledCourses.toList())

    LaunchedEffect(selectedSubject) {
        isLoading = true
        errorMessage = null
        try {
            courses = CourseRepository.getCourses(term = "1259", subject = selectedSubject)
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
                onCoursesClick = { /* already here */ },
                onAiClick = { /* TODO */ },
                onScheduleClick = onBackToHome,
                onChatbotClick = onNavigateToChatbot,
                onAdvisorClick = { /* TODO */ },
                showImportExport = false
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(primaryYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Daniel",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "View Profile",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.clickable { onViewProfile() }
                        )
                    }
                }

                TextButton(onClick = onLogout) {
                    Text(
                        text = "→ Logout",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Subject chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("CS", "MATH", "PHYS", "CHEM", "ENG")) { subject ->
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
                            selected = true
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
                                        term = "1259",
                                        units = group.units
                                    )
                                    if (!AppState.scheduledCourses.any {
                                            it.code == newCourse.code &&
                                                    it.section.classNumber == newCourse.section.classNumber
                                        }) {
                                        AppState.scheduledCourses.add(newCourse)
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
    onAddSection: (Section) -> Unit
) {
    val primaryYellow = Color(0xFFFFD700)
    val scope = rememberCoroutineScope()  // Local scope for this composable

    val addedCount = group.sections.count { section ->
        scheduledCourses.any {
            it.code == group.code && it.section.classNumber == section.section.classNumber
        }
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

                if (addedCount > 0) {
                    Text(
                        text = "$addedCount added",
                        color = primaryYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                        val isAdded = scheduledCourses.any {
                            it.code == group.code && it.section.classNumber == section.classNumber
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = section.component,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = section.componentType,
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }

                                Text(
                                    text = section.days.joinToString(", ") + " " +
                                            "${section.startTime}-${section.endTime}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF666666)
                                )
                            }

                            if (isAdded) {
                                Button(
                                    onClick = {
                                        val toRemove = scheduledCourses.find {
                                            it.code == group.code && it.section.classNumber == section.classNumber
                                        }
                                        if (toRemove != null) {
                                            AppState.scheduledCourses.remove(toRemove)
                                            scope.launch {
                                                CourseRepository.saveUserSchedule(AppState.scheduledCourses)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryYellow.copy(alpha = 0.15f),
                                        contentColor = primaryYellow
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("✓ Added", fontSize = 14.sp)
                                }
                            } else {
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

                        if (index < group.sections.size - 1) {
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                        }
                    }
                }
            }
        }
    }
}