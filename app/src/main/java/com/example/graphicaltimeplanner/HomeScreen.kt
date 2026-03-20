// HomeScreen.kt
package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToTimetable: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onLogout: () -> Unit
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFFDFDFD)

    val coroutineScope = rememberCoroutineScope()

    val scheduledCourses by rememberUpdatedState(AppState.scheduledCourses.toList())

    val courseColors = remember(scheduledCourses) {
        val palette = listOf(
            Color(0xFFFFD700), Color(0xFFE1BEE7), Color(0xFFBBDEFB),
            Color(0xFFC8E6C9), Color(0xFFFFF9C4)
        )
        val map = mutableMapOf<String, Color>()
        scheduledCourses.distinctBy { it.code }.forEachIndexed { i, c ->
            map[c.code] = palette[i % palette.size]
        }
        map
    }

    LaunchedEffect(Unit) {
        val saved = CourseRepository.loadUserSchedule()
        if (AppState.scheduledCourses.isEmpty()) {
            AppState.scheduledCourses.clear()
            AppState.scheduledCourses.addAll(saved)
        }
    }

    LaunchedEffect(scheduledCourses) {
        CourseRepository.saveUserSchedule(scheduledCourses)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = lightBackground,
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.SCHEDULE,
                onCoursesClick = onNavigateToCourses,
                onAiClick = onNavigateToAssistant,
                onScheduleClick = onNavigateToTimetable,
                onChatbotClick = onNavigateToChatbot,
                onAdvisorClick = { /* TODO */ },
                showImportExport = true,
                onImportClick = { /* TODO */ },
                onExportClick = { /* TODO */ }
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
                        Text("D", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Daniel", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "View Profile",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.clickable { /* TODO */ }
                        )
                    }
                }

                TextButton(onClick = onLogout) {
                    Text("→ Logout", color = Color.Gray, fontSize = 15.sp)
                }
            }

            // My Timetable header + clear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Timetable",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = {
                    coroutineScope.launch {
                        AppState.scheduledCourses.clear()
                        CourseRepository.saveUserSchedule(emptyList())
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (scheduledCourses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Courses Added",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Add courses to see your weekly schedule",
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = onNavigateToCourses,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Add Courses", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    TimetableView(
                        courses = scheduledCourses,
                        courseColors = courseColors,
                        onRemoveCourse = { courseToRemove ->
                            coroutineScope.launch {
                                AppState.scheduledCourses.remove(courseToRemove)
                                CourseRepository.saveUserSchedule(AppState.scheduledCourses)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimetableView(
    courses: List<Course>,
    courseColors: Map<String, Color>,
    onRemoveCourse: (Course) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 23
    val hourHeight = 60.dp

    val primaryYellow = Color(0xFFFFD700)

    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val totalWidth = maxWidth
        val timeColumnWidth = 48.dp
        val columnWidth = (totalWidth - timeColumnWidth) / days.size

        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        Column(modifier = Modifier.fillMaxSize()) {
            // Header row - days
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(timeColumnWidth))

                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(columnWidth)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            fontWeight = FontWeight.Bold,
                            color = primaryYellow,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Time labels
                    Column(
                        modifier = Modifier.width(timeColumnWidth),
                        horizontalAlignment = Alignment.End
                    ) {
                        (startHour..endHour).forEach { hour ->
                            Box(
                                modifier = Modifier
                                    .height(hourHeight)
                                    .padding(end = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%d:00", hour),
                                    fontSize = 12.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        }
                    }

                    // Main grid area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(((endHour - startHour + 1) * hourHeight.value).dp)
                            .background(Color.White)
                            .drawBehind {
                                val strokeWidth = with(density) { 1.dp.toPx() }
                                val gray = Color(0xFFE0E0E0)

                                // Horizontal hour lines
                                repeat(endHour - startHour + 1) { i ->
                                    val y = i * hourHeight.toPx()
                                    drawLine(gray, Offset(0f, y), Offset(size.width, y), strokeWidth)
                                }

                                // Vertical day lines (including left and right edges)
                                repeat(days.size + 1) { i ->
                                    val x = i * (size.width / days.size)
                                    drawLine(gray, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                                }
                            }
                    ) {
                        courses.forEach { course ->
                            course.section.days.forEach { dayStr ->
                                val dayIdx = days.indexOf(dayStr)
                                if (dayIdx < 0) return@forEach

                                val startTime = course.section.startTime
                                val endTime = course.section.endTime

                                val startFloat = startTime.toFloat()
                                val endFloat = endTime.toFloat()
                                val durationHours = endFloat - startFloat

                                if (durationHours <= 0f) return@forEach

                                val topOffset = ((startFloat - startHour) * hourHeight.value).dp
                                val blockHeight = (durationHours * hourHeight.value).dp

                                val color = courseColors[course.code] ?: primaryYellow

                                Card(
                                    modifier = Modifier
                                        .offset(
                                            x = columnWidth * dayIdx + (columnWidth * 0.04f),
                                            y = topOffset
                                        )
                                        .width(columnWidth * 0.92f)
                                        .height(blockHeight)
                                        .padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = color),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    onClick = { selectedCourse = course }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = course.code,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = course.section.component,
                                            fontSize = 11.sp,
                                            color = Color.Black.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "$startTime-$endTime",
                                            fontSize = 10.sp,
                                            color = Color.Black.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Course detail dialog
        selectedCourse?.let { course ->
            AlertDialog(
                onDismissRequest = { selectedCourse = null },
                title = { Text("${course.code} – ${course.section.component}") },
                text = {
                    Column {
                        Text("Title: ${course.title}")
                        Text("Time: ${course.section.days.joinToString(", ")} ${course.section.startTime}–${course.section.endTime}")
                        Text("Location: ${course.section.location}")
                        Text("Units: ${course.units}")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemoveCourse(course)
                            selectedCourse = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedCourse = null }) { Text("Close") }
                }
            )
        }
    }
}