// AppState.kt
package com.example.graphicaltimeplanner

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class Timetable(
    val id: String,
    val name: String,
    val term: String,
    val courses: List<Course>
)

object AppState {
    // ── Active courses (current timetable) ────────────────────────────────────
    val scheduledCourses = mutableStateListOf<Course>()

    // ── Multiple timetables ───────────────────────────────────────────────────
    val timetables = mutableStateListOf<Timetable>()
    val activeTimetableId = mutableStateOf<String?>(null)

    // ── User info ─────────────────────────────────────────────────────────────
    val displayName = mutableStateOf("")

    fun clearSchedule() {
        scheduledCourses.clear()
    }

    fun logout() {
        scheduledCourses.clear()
        timetables.clear()
        activeTimetableId.value = null
        displayName.value = ""
    }
}
