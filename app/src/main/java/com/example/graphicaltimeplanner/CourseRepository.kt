package com.example.graphicaltimeplanner

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val TAG = "CourseRepo"

// Singleton Repository
object CourseRepository {

    private val db get() = FirebaseFirestore.getInstance()

    // Comprehensive list of UWaterloo subjects
    val ALL_SUBJECTS = listOf(
        "ACC", "ACTSC", "AFM", "AMATH", "ANTH", "APPLS", "ARBUS", "ARCH",
        "BIOL", "BME", "BUS",
        "CHEM", "CHINA", "CIVE", "CLAS", "CM", "CO", "COMM", "CS",
        "EARTH", "ECE", "ECON", "EMLS", "ENGL", "ENVE", "ENVS", "ERS",
        "FINE", "FR",
        "GBDA", "GENE", "GEOE", "GEOG", "GER", "GERON",
        "HIST", "HLTH", "HRM",
        "INTEG", "ITAL",
        "JAPAN",
        "KIN", "KOREA",
        "MATH", "ME", "MGMT", "MSCI", "MTE", "MUSIC",
        "NE",
        "PACS", "PD", "PHIL", "PHYS", "PLAN", "PMATH", "PS", "PSCI", "PSYCH",
        "REC", "RS",
        "SCI", "SE", "SI", "SMF", "SOC", "SOCWK", "SPAN", "SPCOM", "STAT", "STV", "SYDE",
        "WS"
    )

    /**
     * Fetch courses from Firestore for a given term and subject.
     *
     * Strategy: query with single-field equality on "subject" (auto-indexed,
     * no composite index required), then filter by term client-side.
     */
    suspend fun getCourses(term: String, subject: String): List<Course> {
        val courses = mutableListOf<Course>()
        Log.d(TAG, "Fetching courses: term=$term, subject=$subject")

        // Single-field whereEqualTo uses an auto-created index — no composite
        // index needed.  Filter by term on the client side.
        val querySnapshot = db.collection("courses")
            .whereEqualTo("subject", subject)
            .get()
            .await()

        Log.d(TAG, "Query returned ${querySnapshot.documents.size} docs for subject=$subject")

        for (document in querySnapshot.documents) {
            try {
                // Client-side term filter
                val docTerm = document.getString("term") ?: ""
                if (docTerm != term) continue

                val code = document.getString("catalog") ?: ""
                val title = document.getString("title") ?: ""

                // 'sections' is an array of maps
                val rawSections = document.get("sections") as? List<Map<String, Any>> ?: emptyList()
                Log.d(TAG, "  Doc ${document.id}: ${rawSections.size} sections")

                for (secMap in rawSections) {
                    val timeDateStr = secMap["time_date"] as? String ?: ""
                    val location = secMap["location"] as? String ?: ""
                    val component = secMap["component"] as? String ?: ""
                    val classNum = secMap["class"] as? String ?: ""

                    // Parse time string (e.g. "10:00-11:20TTh")
                    val timeData = TimeParser.parse(timeDateStr)

                    if (timeData != null) {
                        val (start, end, days) = timeData
                        courses.add(
                            Course(
                                code = "$subject $code",
                                title = title,
                                section = Section(
                                    classNumber = classNum,
                                    component = component,
                                    days = days,
                                    startTime = start,
                                    endTime = end,
                                    location = location
                                ),
                                term = term
                            )
                        )
                    } else {
                        // Sections with no parseable time (TBA / online)
                        // Still show them in the list so users can add them
                        courses.add(
                            Course(
                                code = "$subject $code",
                                title = title,
                                section = Section(
                                    classNumber = classNum,
                                    component = component,
                                    days = emptyList(),
                                    startTime = Time(0, 0),
                                    endTime = Time(0, 0),
                                    location = location.ifBlank { "TBA" }
                                ),
                                term = term
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing doc ${document.id}", e)
            }
        }

        Log.d(TAG, "Returning ${courses.size} course sections for $subject $term")

        return courses
    }
}

object TimeParser {
    // TTh -> Tue, Thu
    // MWF -> Mon, Wed, Fri
    // 10:00-11:20MW
    
    fun parse(timeStr: String): Triple<Time, Time, List<String>>? {
        if (timeStr.isBlank()) return null
        
        // Regex for HH:MM-HH:MM
        // e.g. 08:30-09:20MWF
        val timeRegex = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")
        val match = timeRegex.find(timeStr) ?: return null
        
        val (h1, m1, h2, m2) = match.destructured
        
        val start = Time(h1.toInt(), m1.toInt())
        val end = Time(h2.toInt(), m2.toInt())
        
        // Extract rest of string as days
        // The string might have dates at the end, e.g. "02:30-03:20MWF05/05-07/30"
        // We only want the letters immediately following the time.
        val daysPart = timeStr.substring(match.range.last + 1).trim()
        
        // Extract just the letters (M, T, W, Th, F) before any numbers (dates)
        val daysLetters = daysPart.takeWhile { it.isLetter() }
        val days = parseDays(daysLetters)
        
        return Triple(start, end, days)
    }
    
    private fun parseDays(s: String): List<String> {
        val days = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                'M' -> days.add("Mon")
                'T' -> {
                    if (i + 1 < s.length && s[i+1] == 'h') {
                        days.add("Thu")
                        i++ 
                    } else {
                        days.add("Tue")
                    }
                }
                'W' -> days.add("Wed")
                'F' -> days.add("Fri")
            }
            i++
        }
        return days
    }
}
