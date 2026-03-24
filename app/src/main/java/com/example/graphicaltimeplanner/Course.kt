package com.example.graphicaltimeplanner

data class Time(
    val hour: Int,
    val minute: Int
) : Comparable<Time> {
    override fun compareTo(other: Time): Int {
        if (this.hour != other.hour) return this.hour - other.hour
        return this.minute - other.minute
    }

    override fun toString(): String {
        return "%02d:%02d".format(hour, minute)
    }

    fun toFloat(): Float {
        return hour + minute / 60f
    }
}

data class Section(
    val classNumber: String, // e.g. 6469
    val component: String,   // e.g. LEC 001
    val days: List<String>,  // e.g. ["Tue", "Thu"]
    val startTime: Time,     // e.g. 10:00
    val endTime: Time,       // e.g. 11:20
    val location: String     // e.g. RCH 101
) {
    val componentType: String
        get() = component.split(" ").firstOrNull() ?: ""
}

data class Course(
    val code: String,   // e.g. "CS 136"
    val title: String,  // e.g. "Elementary Algorithm Design and Data Abstraction"
    val section: Section,
    val term: String,   // e.g. "1261"
    val units: String   // e.g. "0.5"
)