package com.example.graphicaltimeplanner

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

data class Section(
    val day: String,
    val startHour: Int,
    val endHour: Int
)

data class Course(
    val code: String,
    val section: Section
)