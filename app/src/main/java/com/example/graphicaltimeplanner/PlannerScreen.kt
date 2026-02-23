package com.example.graphicaltimeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.graphicaltimeplanner.ui.theme.GraphicalTimePlannerTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.times
import androidx.compose.ui.Alignment

@Composable
fun PlannerScreen() {
    var scheduledCourses by remember { mutableStateOf(listOf<Course>()) }
    var selectedTerm by remember { mutableStateOf("1261") } // 1261 = Winter 2026
    var selectedSubject by remember { mutableStateOf("CS") }
    var termExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Timetable",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clear Button
                Button(
                    onClick = { scheduledCourses = emptyList() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("Clear", color = Color.White)
                }

                // Term Selector Dropdown
                Box {
                    Button(
                        onClick = { termExpanded = !termExpanded },
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4))
                    ) {
                        Text(text = when(selectedTerm) {
                            "1261" -> "Winter 2026"
                            "1259" -> "Fall 2025"
                            "1255" -> "Spring 2025"
                            else -> selectedTerm
                        })
                    }
                    DropdownMenu(
                        expanded = termExpanded,
                        onDismissRequest = { termExpanded = false }
                    ) {
                        listOf(
                            "1261" to "Winter 2026",
                            "1259" to "Fall 2025",
                            "1255" to "Spring 2025"
                        ).forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (selectedTerm != code) {
                                        selectedTerm = code
                                        scheduledCourses = emptyList() // Clear when switching semesters
                                    }
                                    termExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Timetable View (Top part) ---
        Card(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            TimetableView(
                modifier = Modifier.fillMaxSize(),
                courses = scheduledCourses,
                onRemoveCourse = { course ->
                    scheduledCourses = scheduledCourses - course
                }
            )
        }

        // --- Course Selection Area (Bottom part) ---
        Column(modifier = Modifier.weight(0.45f)) {
            
            Text(
                "Add Courses",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Subject Filter Bar
            SubjectSelector(
                selectedSubject = selectedSubject,
                onSubjectSelected = { selectedSubject = it }
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Course List from Firestore
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                CourseList(
                    term = selectedTerm,
                    subject = selectedSubject,
                    modifier = Modifier.fillMaxSize(),
                    onCourseSelected = { course ->
                        val alreadyAdded = scheduledCourses.any { 
                            it.code == course.code && it.section.component == course.section.component 
                        }
                        
                        // Simple conflict check
                        val conflict = isConflict(course, scheduledCourses)

                        if (!alreadyAdded && !conflict) {
                            scheduledCourses = scheduledCourses + course
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SubjectSelector(
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSubjects = if (searchQuery.isBlank()) {
        CourseRepository.ALL_SUBJECTS
    } else {
        CourseRepository.ALL_SUBJECTS.filter {
            it.contains(searchQuery, ignoreCase = true)
        }
    }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search subject (e.g. CS, MATH)...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredSubjects) { subject ->
                FilterChip(
                    selected = (subject == selectedSubject),
                    onClick = { onSubjectSelected(subject) },
                    label = { Text(subject) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorResource(R.color.uw_gold_lvl4),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
    }
}

@Composable
fun CourseList(
    term: String,
    subject: String,
    modifier: Modifier = Modifier,
    onCourseSelected: (Course) -> Unit
) {
    // Determine state
    // We should launch a coroutine to fetch data when term/subject changes
    var courses by remember { mutableStateOf(listOf<Course>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(term, subject) {
        isLoading = true
        errorMsg = null
        try {
            courses = CourseRepository.getCourses(term, subject)
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unknown error"
            courses = emptyList()
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colorResource(R.color.uw_gold_lvl4))
        }
    } else if (errorMsg != null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Error loading courses:\n$errorMsg",
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else if (courses.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No courses found for $subject in this term.\nTap a different subject or switch terms.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(12.dp)
        ) {
            items(courses) { course ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onCourseSelected(course) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.code, // e.g. "CS 136"
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = course.title,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                maxLines = 1
                            )
                        }
                        
                        // Section Info
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = course.section.component, // e.g. "LEC 001"
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (course.section.days.isEmpty()) "TBA"
                                       else "${course.section.days.joinToString(",")} ${course.section.startTime}-${course.section.endTime}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun isConflict(newCourse: Course, existing: List<Course>): Boolean {
    for (course in existing) {
        val commonDays = course.section.days.intersect(newCourse.section.days.toSet())
        if (commonDays.isNotEmpty()) {
            val startA = course.section.startTime
            val endA = course.section.endTime
            val startB = newCourse.section.startTime
            val endB = newCourse.section.endTime

            if (startB < endA && endB > startA) {
                return true
            }
        }
    }
    return false
}



@Composable
fun TimetableView(
    modifier: Modifier = Modifier,
    courses: List<Course>,
    onRemoveCourse: (Course) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 20
    val hourHeight = 60.dp  // Fixed height for one hour
    
    // We use BoxWithConstraints to calculate widths dynamically so no horizontal scroll is needed
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(8.dp)) {
        val totalWidth = maxWidth
        val timeColumnWidth = 50.dp
        val dayColumnWidth = (totalWidth - timeColumnWidth) / days.size

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row: Days
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(timeColumnWidth))
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.uw_gold_lvl4)
                        )
                    }
                }
            }

            // Timeline Area (Vertical Scroll only)
            val verticalScrollState = rememberScrollState()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Time Labels Column
                    Column(
                        modifier = Modifier.width(timeColumnWidth),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (hour in startHour..endHour) {
                            Text(
                                text = String.format("%02d:00", hour),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier
                                    .height(hourHeight)
                                    .padding(end = 4.dp)
                            )
                        }
                    }

                    // Grid & Courses
                    Box(modifier = Modifier.fillMaxWidth()) {
                        
                        // 1. Draw Grid Lines
                        Column {
                            for (hour in startHour..endHour) {
                                Divider(
                                    color = Color.LightGray.copy(alpha = 0.5f),
                                    modifier = Modifier.height(hourHeight)
                                )
                            }
                        }
                        
                        // Vertical Dividers for days
                        Row {
                            repeat(days.size) {
                                Box(
                                    modifier = Modifier
                                        .width(dayColumnWidth)
                                        .fillMaxHeight() // This doesn't work well in scrollable, but logic handles it
                                        .border(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
                                )
                            }
                        }

                        // 2. Plot Courses
                        courses.forEach { course ->
                            course.section.days.forEach { day ->
                                val dayIndex = days.indexOf(day)
                                if (dayIndex >= 0) {
                                    val topOffset = (course.section.startTime.toFloat() - startHour) * hourHeight
                                    val height = (course.section.endTime.toFloat() - course.section.startTime.toFloat()) * hourHeight
                                    
                                    if (height > 0.dp) {
                                        Card(
                                            modifier = Modifier
                                                .absoluteOffset(x = dayColumnWidth * dayIndex, y = topOffset)
                                                .width(dayColumnWidth)
                                                .height(height)
                                                .padding(2.dp),
                                            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.9f)),
                                            elevation = CardDefaults.cardElevation(2.dp),
                                            onClick = { onRemoveCourse(course) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(2.dp),
                                                verticalArrangement = Arrangement.Top
                                            ) {
                                                Text(
                                                    text = course.code, 
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black,
                                                    maxLines = 1,
                                                    lineHeight = 10.sp
                                                )
                                                Text(
                                                    text = course.section.component, 
                                                    fontSize = 8.sp,
                                                    maxLines = 1,
                                                    lineHeight = 8.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

