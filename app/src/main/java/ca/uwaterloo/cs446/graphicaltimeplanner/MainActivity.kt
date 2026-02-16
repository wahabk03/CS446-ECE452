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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
                scheduledCourses = scheduledCourses + course
            }
        )

        TimetableView(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            courses = scheduledCourses
        )
    }
}


@Composable
fun CourseList(
    modifier: Modifier = Modifier,
    onCourseSelected: (Course) -> Unit
) {
    val courses = listOf(
        Course("CS446", Section("Mon", 9, 11)),
        Course("ECE452", Section("Tue", 10, 12)),
        Course("STAT341", Section("Wed", 13, 15)),
        Course("MATH239", Section("Thu", 8, 10)),
        Course("PHYS115", Section("Fri", 14, 16))
    )

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
    courses: List<Course>
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(8.dp)
    ) {
        Text("Timetable")

        Spacer(modifier = Modifier.height(16.dp))

        courses.forEach { course ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFBBDEFB)
                )
            ) {
                Text(
                    text = "${course.code} - ${course.section.day} ${course.section.startHour}:00-${course.section.endHour}:00",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
