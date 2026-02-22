package com.example.graphicaltimeplanner

data class Section(
    val day: String,
    val startHour: Int,
    val endHour: Int
)

data class Course(
    val code: String,
    val section: Section
)