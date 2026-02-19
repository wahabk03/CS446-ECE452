package ca.uwaterloo.cs446.graphicaltimeplanner

object CourseRepository {

    fun getCourses(): List<Course> {
        return listOf(
            Course("CS446", Section("Mon", 9, 11)),
            Course("ECE452", Section("Tue", 10, 12)),
            Course("STAT341", Section("Wed", 13, 15)),
            Course("MATH239", Section("Thu", 8, 10)),
            Course("PHYS115", Section("Fri", 14, 16))
        )
    }
}
