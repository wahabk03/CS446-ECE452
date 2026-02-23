package com.example.graphicaltimeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.graphicaltimeplanner.ui.theme.GraphicalTimePlannerTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.times
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PlannerScreen() {
    var scheduledCourses by remember { mutableStateOf(listOf<Course>()) }

    // Use a Column layout for mobile responsiveness, or BoxWithConstraints if you really want split pane
    // Simplified to a Column since dual-pane is tricky on small screens without more setup
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f)) // Light background
            .padding(16.dp)
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ){
            Text(
                text = "My Timetable",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            Button(
                onClick = {
                    println("Button clicked")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.uw_gold_lvl4),
                    contentColor = Color.Black
                )
            )
            {
                Text(
                    text = "Import"
                )

            }
        }


        // Timetable Section (Top 60%)
        Card(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selected Courses",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Course List Section (Bottom 40%)
        Card(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            CourseList(
                modifier = Modifier.fillMaxSize(),
                onCourseSelected = { course ->
                    val alreadyAdded = scheduledCourses.any { it.code == course.code }
                    val conflict = isConflict(course, scheduledCourses)

                    if (!alreadyAdded && !conflict) {
                        scheduledCourses = scheduledCourses + course
                    }
                    else if (alreadyAdded) {
                        scheduledCourses = scheduledCourses - course
                    }
                }
            )
        }
    }
}

fun isConflict(newCourse: Course, existing: List<Course>): Boolean {
    for (course in existing) {

        if (course.section.day == newCourse.section.day) {

            val startA = course.section.startHour
            val endA = course.section.endHour
            val startB = newCourse.section.startHour
            val endB = newCourse.section.endHour

            if (startB < endA && endB > startA) {
                return true
            }
        }
    }
    return false
}


@Composable
fun CourseList(
    modifier: Modifier = Modifier,
    onCourseSelected: (Course) -> Unit
) {
    val courses = CourseRepository.getCourses()

    LazyColumn(
        modifier = modifier.padding(12.dp)
    ) {
        items(courses) { course ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = {
                    onCourseSelected(course)
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorResource(R.color.uw_gold_lvl4))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = course.code,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${course.section.day} ${course.section.startHour}:00 - ${course.section.endHour}:00",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
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
    onRemoveCourse: (Course) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 20
    val hourHeight = 60.dp  // Fixed height for one hour
    
    // We use BoxWithConstraints to calculate widths dynamically so no horizontal scroll is needed
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(8.dp)) {
        val totalWidth = this.maxWidth
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
                            val dayIndex = days.indexOf(course.section.day)
                            if (dayIndex >= 0) {
                                val topOffset = (course.section.startHour - startHour) * hourHeight
                                val height = (course.section.endHour - course.section.startHour) * hourHeight

                                Card(
                                    modifier = Modifier
                                        .offset(x = (dayIndex * dayColumnWidth), y = topOffset)
                                        .width(dayColumnWidth)
                                        .height(height)
                                        .padding(2.dp),
                                    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.9f)),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    onClick = { onRemoveCourse(course) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = course.code,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
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

