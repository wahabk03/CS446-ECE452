// AppState.kt
package com.example.graphicaltimeplanner

import androidx.compose.runtime.mutableStateListOf

object AppState {
    val scheduledCourses = mutableStateListOf<Course>()

    fun clearSchedule() {
        scheduledCourses.clear()
    }
}