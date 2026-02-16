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
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
fun MainScreen() {
    Row(modifier = Modifier.fillMaxSize()) {

        // Left: Course List
        CourseList(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // Right: Timetable Area
        TimetableView(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun CourseList(modifier: Modifier = Modifier) {
    val courses = listOf("CS446", "ECE452", "STAT341", "MATH239", "PHYS115")

    LazyColumn(
        modifier = modifier
            .background(Color.LightGray)
            .padding(8.dp)
    ) {
        items(courses) { course ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = course,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun TimetableView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White)
            .padding(8.dp)
    ) {
        Text("Timetable Area")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GraphicalTimePlannerTheme {
        Greeting("Android")
    }
}