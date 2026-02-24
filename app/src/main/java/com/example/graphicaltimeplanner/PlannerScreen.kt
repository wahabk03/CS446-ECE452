package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

val COURSE_COLORS = listOf(
    Color(0xFFE57373), // Red
    Color(0xFF81C784), // Green
    Color(0xFF64B5F6), // Blue
    Color(0xFFFFD54F), // Yellow
    Color(0xFFBA68C8), // Purple
    Color(0xFFFFB74D), // Orange
    Color(0xFF4DB6AC), // Teal
    Color(0xFFA1887F), // Brown
    Color(0xFFEF9A9A), // Light Red
    Color(0xFFA5D6A7), // Light Green
    Color(0xFF90CAF9), // Light Blue
    Color(0xFFFFE082), // Light Yellow
    Color(0xFFCE93D8), // Light Purple
    Color(0xFFFFCC80), // Light Orange
    Color(0xFF80CBC4), // Light Teal
    Color(0xFFBCAAA4)  // Light Brown
)

@Composable
fun PlannerScreen(
    onLogout: () -> Unit = {}
) {
    var scheduledCourses by remember { mutableStateOf(listOf<Course>()) }
    var courseColors by remember { mutableStateOf(mapOf<String, Color>()) }
    var selectedTerm by remember { mutableStateOf("1261") } // 1261 = Winter 2026
    var selectedSubject by remember { mutableStateOf("CS") }
    var termExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Load saved schedule on startup
    LaunchedEffect(Unit) {
        val saved = CourseRepository.loadUserSchedule()
        scheduledCourses = saved
        // Restore colors
        val colors = mutableMapOf<String, Color>()
        val usedColors = mutableSetOf<Color>()
        saved.forEach { course ->
            if (!colors.containsKey(course.code)) {
                val available = COURSE_COLORS - usedColors
                val color = available.firstOrNull() ?: COURSE_COLORS.random()
                colors[course.code] = color
                usedColors.add(color)
            }
        }
        courseColors = colors
    }

    // Helper to save after any schedule change
    fun saveSchedule(courses: List<Course>) {
        scope.launch {
            CourseRepository.saveUserSchedule(courses)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f))
    ) {
        // --- Top Decorative Banner ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(colorResource(R.color.uw_gold_lvl4))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    // Log Out Button
                    Button(
                        onClick = { /* TODO: Implement Log Out */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Log Out", color = Color.White)
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
                    courses = scheduledCourses.filter { it.term == selectedTerm },
                    courseColors = courseColors,
                    onClearAll = {
                        val remaining = scheduledCourses.filter { it.term != selectedTerm }
                        val removedCodes = scheduledCourses.filter { it.term == selectedTerm }.map { it.code }.toSet()
                        val stillUsedCodes = remaining.map { it.code }.toSet()
                        courseColors = courseColors.filterKeys { it in stillUsedCodes }
                        scheduledCourses = remaining
                        saveSchedule(remaining)
                    },
                    onRemoveCourse = { course ->
                        val updated = scheduledCourses - course
                        scheduledCourses = updated
                        if (updated.none { it.code == course.code }) {
                            courseColors = courseColors - course.code
                        }
                        saveSchedule(updated)
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
                searchQuery = searchQuery,
                onSearchQueryChange = { 
                    searchQuery = it 
                    // Auto-select subject if the query starts with a valid subject
                    val letters = it.takeWhile { char -> char.isLetter() }.uppercase()
                    if (CourseRepository.ALL_SUBJECTS.contains(letters)) {
                        selectedSubject = letters
                    }
                },
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
                    searchQuery = searchQuery,
                    modifier = Modifier.fillMaxSize(),
                    onCourseSelected = { course ->
                        // Extract the category (e.g., "LEC", "TUT", "LAB") from the component string (e.g., "LEC 001")
                        val category = course.section.component.split(" ").firstOrNull() ?: ""

                        // Replace if same course code AND same category is already added
                        val filtered = scheduledCourses.filter {
                            !(it.code == course.code && (it.section.component.split(" ").firstOrNull() ?: "") == category)
                        }
                        val updated = filtered + course
                        scheduledCourses = updated

                        if (!courseColors.containsKey(course.code)) {
                            val usedColors = courseColors.values.toSet()
                            val availableColors = COURSE_COLORS - usedColors
                            val newColor = availableColors.firstOrNull() ?: COURSE_COLORS.random()
                            courseColors = courseColors + (course.code to newColor)
                        }
                        saveSchedule(updated)
                    }
                )
            }
        }
    }
}
}

@Composable
fun SubjectSelector(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit
) {
    val subjectQuery = searchQuery.takeWhile { it.isLetter() }.uppercase()
    val filteredSubjects = if (subjectQuery.isBlank()) {
        CourseRepository.ALL_SUBJECTS
    } else {
        CourseRepository.ALL_SUBJECTS.filter {
            it.contains(subjectQuery, ignoreCase = true)
        }
    }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search (e.g. CS, MATH, CS 446)...") },
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
    searchQuery: String,
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

    val filteredCourses = remember(courses, searchQuery) {
        if (searchQuery.isBlank()) {
            courses
        } else {
            val normalizedQuery = searchQuery.replace(" ", "").lowercase()
            courses.filter { course ->
                val normalizedCourseCode = course.code.replace(" ", "").lowercase()
                normalizedCourseCode.contains(normalizedQuery) || 
                course.title.lowercase().contains(searchQuery.lowercase())
            }
        }
    }

    val groupedCourses = remember(filteredCourses) {
        filteredCourses.groupBy { it.code }
    }
    
    var expandedCourses by remember { mutableStateOf(setOf<String>()) }

    // Auto-expand if there's only one course matching the search
    LaunchedEffect(groupedCourses) {
        if (searchQuery.isNotBlank() && groupedCourses.size == 1) {
            expandedCourses = expandedCourses + groupedCourses.keys.first()
        }
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
    } else if (filteredCourses.isEmpty()) {
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
            groupedCourses.forEach { (courseCode, sections) ->
                val isExpanded = expandedCourses.contains(courseCode)
                val courseTitle = sections.firstOrNull()?.title ?: ""
                
                item(key = courseCode) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { 
                            expandedCourses = if (isExpanded) expandedCourses - courseCode else expandedCourses + courseCode 
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = courseCode, // e.g. "CS 136"
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = courseTitle,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = if (isExpanded) "▲" else "▼",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                if (isExpanded) {
                    items(sections, key = { it.section.component + it.code }) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp),
                            onClick = { onCourseSelected(course) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
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
                                Text(
                                    text = "+",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorResource(R.color.uw_gold_lvl4)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableView(
    modifier: Modifier = Modifier,
    courses: List<Course>,
    courseColors: Map<String, Color>,
    onClearAll: () -> Unit,
    onRemoveCourse: (Course) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 22
    val hourHeight = 60.dp  // Fixed height for one hour
    
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    
    val dayLayouts = remember(courses) {
        days.associateWith { day ->
            val dayCourses = courses.filter { day in it.section.days }
            val adj = mutableMapOf<Course, MutableList<Course>>()
            dayCourses.forEach { adj[it] = mutableListOf() }
            for (i in dayCourses.indices) {
                for (j in i + 1 until dayCourses.size) {
                    val c1 = dayCourses[i]
                    val c2 = dayCourses[j]
                    if (c1.section.startTime < c2.section.endTime && c2.section.startTime < c1.section.endTime) {
                        adj[c1]!!.add(c2)
                        adj[c2]!!.add(c1)
                    }
                }
            }
            val visited = mutableSetOf<Course>()
            val components = mutableListOf<List<Course>>()
            for (c in dayCourses) {
                if (c !in visited) {
                    val comp = mutableListOf<Course>()
                    val q = kotlin.collections.ArrayDeque<Course>()
                    q.add(c)
                    visited.add(c)
                    while (q.isNotEmpty()) {
                        val curr = q.removeFirst()
                        comp.add(curr)
                        for (neighbor in adj[curr]!!) {
                            if (neighbor !in visited) {
                                visited.add(neighbor)
                                q.add(neighbor)
                            }
                        }
                    }
                    comp.sortBy { courses.indexOf(it) }
                    components.add(comp)
                }
            }
            
            val layoutMap = mutableMapOf<Course, Pair<Float, Float>>()
            for (comp in components) {
                val n = comp.size
                for ((i, c) in comp.withIndex()) {
                    val (w, x) = when (n) {
                        1 -> 1.0f to 0.0f
                        2 -> if (i == 0) 0.75f to 0.0f else 0.25f to 0.75f
                        3 -> when (i) {
                            0 -> 0.50f to 0.0f
                            1 -> 0.25f to 0.50f
                            else -> 0.25f to 0.75f
                        }
                        else -> when (i) {
                            0 -> 0.25f to 0.0f
                            1 -> 0.25f to 0.25f
                            2 -> 0.25f to 0.50f
                            else -> 0.25f to 0.75f
                        }
                    }
                    layoutMap[c] = w to x
                }
            }
            layoutMap
        }
    }
    
    // We use BoxWithConstraints to calculate widths dynamically so no horizontal scroll is needed
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(8.dp)) {
        val totalWidth = maxWidth
        val timeColumnWidth = 40.dp
        val dayColumnWidth = (totalWidth - timeColumnWidth) / days.size

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row: Days
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.width(timeColumnWidth),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4)),
                        // shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .height(24.dp)
                    ) {
                        Text("Clear", color = Color.White, fontSize = 10.sp)
                    }
                }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((endHour - startHour + 1) * hourHeight)
                            .background(Color.White)
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                val lineColor = Color.LightGray.copy(alpha = 0.5f)
                                // Horizontal lines
                                for (i in 0..(endHour - startHour + 1)) {
                                    val y = i * hourHeight.toPx()
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = strokeWidth
                                    )
                                }
                                // Vertical lines
                                for (i in 0..days.size) {
                                    val x = i * dayColumnWidth.toPx()
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = strokeWidth
                                    )
                                }
                            }
                    ) {
                        // 2. Plot Courses
                        courses.forEach { course ->
                            course.section.days.forEach { day ->
                                val dayIndex = days.indexOf(day)
                                if (dayIndex >= 0) {
                                    val layout = dayLayouts[day]?.get(course)
                                    if (layout != null) {
                                        val (widthFraction, offsetFraction) = layout
                                        val topOffset = (course.section.startTime.toFloat() - startHour) * hourHeight
                                        val height = (course.section.endTime.toFloat() - course.section.startTime.toFloat()) * hourHeight
                                        
                                        if (height > 0.dp) {
                                            val color = courseColors[course.code] ?: Color(0xFFE57373)
                                            Card(
                                                modifier = Modifier
                                                    .absoluteOffset(
                                                        x = dayColumnWidth * dayIndex + dayColumnWidth * offsetFraction, 
                                                        y = topOffset
                                                    )
                                                    .width(dayColumnWidth * widthFraction)
                                                    .height(height)
                                                    .padding(1.dp),
                                                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                                                elevation = CardDefaults.cardElevation(2.dp),
                                                onClick = { selectedCourse = course }
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
                                                        color = Color.DarkGray,
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

    if (selectedCourse != null) {
        AlertDialog(
            onDismissRequest = { selectedCourse = null },
            title = {
                Text(text = "${selectedCourse!!.code} - ${selectedCourse!!.section.component}")
            },
            text = {
                Column {
                    Text(text = "Title: ${selectedCourse!!.title}")
                    Text(text = "Time: ${selectedCourse!!.section.days.joinToString(", ")} ${selectedCourse!!.section.startTime} - ${selectedCourse!!.section.endTime}")
                    Text(text = "Location: ${selectedCourse!!.section.location}")
                    Text(text = "Units: ${selectedCourse!!.units}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveCourse(selectedCourse!!)
                        selectedCourse = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                Button(
                    onClick = { selectedCourse = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

