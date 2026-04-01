// AppState.kt
package com.example.graphicaltimeplanner

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf

data class Timetable(
    val id: String,
    val name: String,
    val term: String = "1261",
    val courses: List<Course>
)

object AppState {
    // ── Active courses (current timetable) ────────────────────────────────────
    val scheduledCourses = mutableStateListOf<Course>()

    // ── Multiple timetables ───────────────────────────────────────────────────
    val timetables = mutableStateListOf<Timetable>()
    val activeTimetableId = mutableStateOf<String?>(null)

    // ── Preference screen: selected courses + pinned sections (persists across navigation, cleared on logout) ──
    val selectedCourseCodes = mutableStateSetOf<String>()
    // Key = "$courseCode||$componentType", value = the pinned Section
    val selectedSections    = mutableStateMapOf<String, Section>()

    // ── Preference screen: scheduling preferences (persists across navigation, cleared on logout) ──
    val avoidEarlyClasses = mutableStateOf(false)
    val earlyClassCutoff  = mutableStateOf(10)    // hour (24-h); classes starting before this are "early"
    val minimizeGaps      = mutableStateOf(false)
    val clusterDays       = mutableStateMapOf("Mon" to false, "Tue" to false, "Wed" to false, "Thu" to false, "Fri" to false)
    val maxDailyHours     = mutableStateOf(8f)

    // ── User info ─────────────────────────────────────────────────────────────
    val displayName = mutableStateOf("")

    fun clearSchedule() {
        scheduledCourses.clear()
    }

    fun logout() {
        // Unsubscribe from all course FCM topics before clearing
        timetables.flatMap { it.courses }
            .map { CourseRepository.courseToTopicName(it) }
            .distinct()
            .forEach {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(it)
            }
        scheduledCourses.clear()
        timetables.clear()
        activeTimetableId.value = null
        displayName.value = ""
        selectedCourseCodes.clear()
        selectedSections.clear()
        avoidEarlyClasses.value = false
        earlyClassCutoff.value  = 10
        minimizeGaps.value      = false
        clusterDays.keys.forEach { clusterDays[it] = false }
        maxDailyHours.value     = 8f
    }
}
