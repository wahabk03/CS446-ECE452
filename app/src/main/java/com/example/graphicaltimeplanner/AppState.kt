// AppState.kt
package com.example.graphicaltimeplanner

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

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

        // Clear any account-scoped chatbot runtime cache to prevent cross-user leakage.
        ChatStateManager.resetAllState()

        scheduledCourses.clear()
        timetables.clear()
        activeTimetableId.value = null
        displayName.value = ""
    }
}
