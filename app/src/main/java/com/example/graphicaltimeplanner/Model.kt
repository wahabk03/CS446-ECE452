package com.example.graphicaltimeplanner

data class Section(
    val day: String,        // Mon
    val startHour: Int,     // 8
    val endHour: Int        // 20
)

data class Course(
    val code: String,
    val section: Section
)