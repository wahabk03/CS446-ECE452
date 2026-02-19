package ca.uwaterloo.cs446.graphicaltimeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ca.uwaterloo.cs446.graphicaltimeplanner.ui.theme.GraphicalTimePlannerTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import android.R.attr.columnWidth
import androidx.compose.ui.unit.times
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GraphicalTimePlannerTheme {
                    MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var scheduledCourses by remember { mutableStateOf(listOf<Course>()) }

    Row(modifier = Modifier.fillMaxSize()) {

        CourseList(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onCourseSelected = { course ->

                val alreadyAdded = scheduledCourses.any { it.code == course.code }
                val conflict = isConflict(course, scheduledCourses)

                if (!alreadyAdded && !conflict) {
                    scheduledCourses = scheduledCourses + course
                }
            }
        )



        TimetableView(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            courses = scheduledCourses,
            onRemoveCourse = { course ->
                scheduledCourses = scheduledCourses - course
            }
        )

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
        modifier = modifier
            .background(Color.LightGray)
            .padding(8.dp)
    ) {
        items(courses) { course ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                onClick = { onCourseSelected(course) }
            ) {
                Text(
                    text = course.code,
                    modifier = Modifier.padding(16.dp)
                )
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
    val endHour = 22
    val halfHourHeight = 40.dp

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {

        Row {

            Spacer(modifier = Modifier.width(60.dp))

            Row(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
            ) {
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .padding(4.dp)
                    ) {
                        Text(text = day)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .width(60.dp)
                    .verticalScroll(verticalScrollState)
            ) {
                for (hour in startHour until endHour) {
                    Text("$hour:00", modifier = Modifier.height(halfHourHeight * 2))
                }
            }

            Box(
                modifier = Modifier
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
            ) {
                Row {

                    days.forEach {

                        Column(
                            modifier = Modifier.width(150.dp)
                        ) {

                            for (hour in startHour until endHour) {

                                Box(
                                    modifier = Modifier
                                        .height(halfHourHeight)
                                        .fillMaxWidth()
                                        .border(0.5.dp, Color.LightGray)
                                )

                                Box(
                                    modifier = Modifier
                                        .height(halfHourHeight)
                                        .fillMaxWidth()
                                        .border(0.5.dp, Color.LightGray)
                                )
                            }
                        }
                    }
                }
                // add courses
                val columnWidth = 150.dp
                courses.forEach { course ->

                    val dayIndex = days.indexOf(course.section.day)

                    if (dayIndex != -1) {

                        val yOffset =
                            ((course.section.startHour - startHour) * 2) * halfHourHeight

                        val height =
                            ((course.section.endHour - course.section.startHour) * 2) * halfHourHeight

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = columnWidth * dayIndex,
                                    y = yOffset
                                )
                                .width(columnWidth)
                                .height(height)
                                .background(Color(0xFF90CAF9))
                                .border(1.dp, Color.Blue)
                        ) {
                            Text(
                                text = course.code,
                                modifier = Modifier.padding(4.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = columnWidth * dayIndex,
                                    y = yOffset
                                )
                                .width(columnWidth)
                                .height(height)
                                .fillMaxSize(), // Makes the Box fill the available screen/parent size
                                contentAlignment = Alignment.TopEnd // Aligns all content inside to the top right
                        ) {
                            TextButton(
                                onClick = { onRemoveCourse(course) }
                            ) {
                                Text(
                                    text = "X",
                                    modifier = Modifier.padding(0.dp) // Optional: Add padding
                                )
                            }
                        }
                    }
                }
            }


        }
    }
}

