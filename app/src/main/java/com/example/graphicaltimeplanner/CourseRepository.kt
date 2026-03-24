package com.example.graphicaltimeplanner

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "CourseRepo"

// Singleton Repository
object CourseRepository {

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val db get() = FirebaseFirestore.getInstance()

    // Comprehensive list of UWaterloo subjects
    val ALL_SUBJECTS = listOf(
        "API"
    )

    // Term mappings
    val TERM_MAPPINGS = listOf(
        "1261" to "Winter 2026",
        "1259" to "Fall 2025",
        "1255" to "Spring 2025"
    )

    /**
     * Fetch courses from Firestore for a given term and subject.
     *
     * Strategy: query with single-field equality on "subject" (auto-indexed,
     * no composite index required), then filter by term client-side.
     */
    suspend fun getCourses(term: String, subject: String): List<Course> {
        val courses = mutableListOf<Course>()
        Log.d(TAG, "Loading courses from bundled assets file: term=$term, subject=$subject")

        return try {
            val context = appContext ?: run {
                Log.e(TAG, "CourseRepository not initialized with context")
                return emptyList()
            }

            val jsonText = context.assets.open("courses.json")
                .bufferedReader()
                .use { it.readText() }

            val rawArray = JSONArray(jsonText)

            for (i in 0 until rawArray.length()) {
                val item = rawArray.getJSONObject(i)

                val docTerm = item.optString("termCode", "")
                if (docTerm != term) continue

                // Since raw API data has no real subject, we treat everything as "API"
                val docSubject = "API"
                if (subject != docSubject) continue

                val courseId = item.optString("courseId", "")
                val title = "Course $courseId"
                val units = "0.5"

                val classNumber = item.opt("classNumber")?.toString() ?: ""
                val component = item.optString("courseComponent", "")
                val scheduleData = item.optJSONArray("scheduleData")

                // No schedule data -> still keep a TBA entry
                if (scheduleData == null || scheduleData.length() == 0) {
                    courses.add(
                        Course(
                            code = "$docSubject $courseId",
                            title = title,
                            section = Section(
                                classNumber = classNumber,
                                component = component,
                                days = emptyList(),
                                startTime = Time(0, 0),
                                endTime = Time(0, 0),
                                location = "TBA"
                            ),
                            term = docTerm,
                            units = units
                        )
                    )
                    continue
                }

                for (j in 0 until scheduleData.length()) {
                    val sched = scheduleData.getJSONObject(j)

                    val startIso =
                        if (sched.isNull("classMeetingStartTime")) "" else sched.optString("classMeetingStartTime", "")
                    val endIso =
                        if (sched.isNull("classMeetingEndTime")) "" else sched.optString("classMeetingEndTime", "")
                    val apiDays = sched.optString("classMeetingDayPatternCode", "")
                    val location = sched.optString("locationName", "").ifBlank { "TBA" }

                    val timeDate = buildLegacyTimeDate(startIso, endIso, apiDays)
                    val timeData = TimeParser.parse(timeDate)

                    if (timeData != null) {
                        val (start, end, days) = timeData
                        courses.add(
                            Course(
                                code = "$docSubject $courseId",
                                title = title,
                                section = Section(
                                    classNumber = classNumber,
                                    component = component,
                                    days = days,
                                    startTime = start,
                                    endTime = end,
                                    location = location
                                ),
                                term = docTerm,
                                units = units
                            )
                        )
                    } else {
                        courses.add(
                            Course(
                                code = "$docSubject $courseId",
                                title = title,
                                section = Section(
                                    classNumber = classNumber,
                                    component = component,
                                    days = emptyList(),
                                    startTime = Time(0, 0),
                                    endTime = Time(0, 0),
                                    location = location.ifBlank { "TBA" }
                                ),
                                term = docTerm,
                                units = units
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "Returning ${courses.size} local asset course sections for $subject $term")
            courses
        } catch (e: Exception) {
            Log.e(TAG, "Error loading courses from assets/courses.json", e)
            emptyList()
        }
    }

    private fun courseToMap(course: Course): Map<String, Any> = mapOf(
        "code" to course.code,
        "title" to course.title,
        "term" to course.term,
        "units" to course.units,
        "classNumber" to course.section.classNumber,
        "component" to course.section.component,
        "days" to course.section.days,
        "startHour" to course.section.startTime.hour,
        "startMinute" to course.section.startTime.minute,
        "endHour" to course.section.endTime.hour,
        "endMinute" to course.section.endTime.minute,
        "location" to course.section.location
    )

    private fun mapToCourse(map: Map<String, Any>): Course = Course(
        code = map["code"] as? String ?: "",
        title = map["title"] as? String ?: "",
        term = map["term"] as? String ?: "",
        units = map["units"] as? String ?: "",
        section = Section(
            classNumber = map["classNumber"] as? String ?: "",
            component = map["component"] as? String ?: "",
            days = (map["days"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            startTime = Time(
                (map["startHour"] as? Long)?.toInt() ?: 0,
                (map["startMinute"] as? Long)?.toInt() ?: 0
            ),
            endTime = Time(
                (map["endHour"] as? Long)?.toInt() ?: 0,
                (map["endMinute"] as? Long)?.toInt() ?: 0
            ),
            location = map["location"] as? String ?: ""
        )
    )

    suspend fun saveUserSchedule(courses: List<Course>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG, "Saving ${courses.size} courses for user $userId")

        try {
            val data = mapOf("scheduledCourses" to courses.map { courseToMap(it) })
            db.collection("users").document(userId).set(data).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user schedule", e)
        }
    }

    suspend fun loadUserSchedule(): List<Course> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
        Log.d(TAG, "Loading schedule for user $userId")

        return try {
            val doc = db.collection("users").document(userId).get().await()
            val rawList = doc.get("scheduledCourses") as? List<Map<String, Any>> ?: emptyList()
            rawList.map { mapToCourse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user schedule", e)
            emptyList()
        }
    }

    suspend fun saveGenerateState(term: String, wishlist: Map<String, List<Course>>, generatedSchedules: List<List<Course>>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG, "Saving assistant state for user $userId term $term. Wishlist size: ${wishlist.size}, Schedules: ${generatedSchedules.size}")

        try {
            val wishlistData = wishlist.mapValues { (_, courses) -> courses.map { courseToMap(it) } }

            // Firestore DOES NOT support nested arrays (List of Lists).
            // We must wrap the inner list in a Map to store it successfully.
            val schedulesData = generatedSchedules.map { schedule ->
                mapOf("courses" to schedule.map { courseToMap(it) })
            }

            val data = mapOf(
                "wishlist" to wishlistData,
                "generatedSchedules" to schedulesData
            )
            db.collection("users").document(userId).collection("assistant").document(term).set(data).await()
            Log.d(TAG, "Successfully saved assistant state")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving assistant state", e)
        }
    }

    suspend fun loadGenerateState(term: String): Pair<Map<String, List<Course>>, List<List<Course>>> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Pair(emptyMap(), emptyList())

        return try {
            val doc = db.collection("users").document(userId).collection("assistant").document(term).get().await()
            if (!doc.exists()) return Pair(emptyMap(), emptyList())

            val rawWishlist = doc.get("wishlist") as? Map<String, List<Map<String, Any>>> ?: emptyMap()
            val wishlist = rawWishlist.mapValues { (_, courses) -> courses.map { mapToCourse(it) } }

            // Unwrap the Map back into a List of Lists
            val rawSchedules = doc.get("generatedSchedules") as? List<Map<String, Any>> ?: emptyList()
            val generatedSchedules = rawSchedules.map { scheduleMap ->
                val coursesList = scheduleMap["courses"] as? List<Map<String, Any>> ?: emptyList()
                coursesList.map { mapToCourse(it) }
            }

            Pair(wishlist, generatedSchedules)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading assistant state", e)
            Pair(emptyMap(), emptyList())
        }
    }

    private var programsCache: List<Program>? = null
    private var advisorsCache: List<Advisor>? = null

    suspend fun getPrograms(): List<Program> {
        programsCache?.let { return it }

        return try {
            val snapshot = db.collection("programs").get().await()
            val programs = snapshot.documents.map { doc ->
                Program(
                    slug = doc.id,
                    name = doc.getString("name") ?: "",
                    faculty = doc.getString("faculty") ?: "",
                    degreeType = doc.getString("degreeType") ?: ""
                )
            }.sortedBy { it.name }
            programsCache = programs
            Log.d(TAG, "Loaded ${programs.size} programs")
            programs
        } catch (e: Exception) {
            Log.e(TAG, "Error loading programs", e)
            emptyList()
        }
    }

    suspend fun getAdvisors(): List<Advisor> {
        advisorsCache?.let { return it }

        return try {
            val snapshot = db.collection("advisors").get().await()
            val advisors = snapshot.documents.map { doc ->
                Advisor(
                    programSlug = doc.getString("programSlug") ?: "",
                    email = doc.getString("email") ?: "",
                    name = doc.getString("name") ?: "",
                    yearLevel = doc.getString("yearLevel") ?: "all",
                    isFallback = doc.getBoolean("isFallback") ?: false,
                    faculty = doc.getString("faculty") ?: ""
                )
            }
            advisorsCache = advisors
            Log.d(TAG, "Loaded ${advisors.size} advisors")
            advisors
        } catch (e: Exception) {
            Log.e(TAG, "Error loading advisors", e)
            emptyList()
        }
    }

    /**
     * Find the correct advisor for a program based on year level.
     * Priority: program-specific match > faculty first-year office > faculty fallback
     */
    fun getAdvisorForProgram(programSlug: String, isFirstYear: Boolean, faculty: String): Advisor? {
        val advisors = advisorsCache ?: return null
        val yearKey = if (isFirstYear) "first-year" else "upper-year"

        // 1. Exact program match for this year level
        val exactMatch = advisors.find {
            it.programSlug == programSlug && (it.yearLevel == yearKey || it.yearLevel == "all")
        }
        if (exactMatch != null) return exactMatch

        // 2. Faculty-level first-year office
        if (isFirstYear) {
            val facultySlug = faculty.lowercase()
            val facultyFirstYear = advisors.find {
                it.programSlug == "$facultySlug-first-year" && it.yearLevel == "first-year"
            }
            if (facultyFirstYear != null) return facultyFirstYear
        }

        // 3. Faculty fallback
        val fallback = advisors.find {
            it.isFallback && it.faculty.equals(faculty, ignoreCase = true)
        }
        return fallback
    }

    suspend fun getUserProfile(): Triple<String?, String?, Int?> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Triple(null, null, null)

        return try {
            val doc = db.collection("users").document(userId).get().await()
            val program = doc.getString("program")
            val faculty = doc.getString("faculty")
            val yearLevel = (doc.getLong("yearLevel"))?.toInt()
            Triple(program, faculty, yearLevel)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile", e)
            Triple(null, null, null)
        }
    }

    suspend fun saveUserProfile(programSlug: String, faculty: String, yearLevel: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        try {
            db.collection("users").document(userId).update(
                mapOf(
                    "program" to programSlug,
                    "faculty" to faculty,
                    "yearLevel" to yearLevel
                )
            ).await()
            Log.d(TAG, "Saved user profile: program=$programSlug, yearLevel=$yearLevel")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
        }
    }



    /**
     * Convert raw Waterloo ClassSchedules API responses into the legacy JSON format
     * expected by the old Firebase-backed course model.
     *
     * Input: a list of raw response strings, each response is a JSON array returned by
     * /v3/ClassSchedules/{term}/{courseId}
     *
     * Output shape:
     * [
     *   {
     *     "subject": "S014418",
     *     "catalog": "C014418",
     *     "title": "T014418",
     *     "term": "1261",
     *     "units": "0.5",
     *     "sections": [
     *       {
     *         "time_date": "14:30-15:50TTh",
     *         "location": "MC 2065",
     *         "component": "SEM",
     *         "class": "7964"
     *       }
     *     ]
     *   }
     * ]
     */
    fun convertRawApiResponsesToLegacyJson(data: List<String>, defaultTerm: String = "1261"): JSONArray {
        val rawFlatJsonArray = JSONArray()

        // Flatten all raw API responses into one large JSON array
        data.forEach { response ->
            val arr = JSONArray(response)
            for (i in 0 until arr.length()) {
                rawFlatJsonArray.put(arr.getJSONObject(i))
            }
        }

        return convertToLegacyFormat(rawFlatJsonArray, defaultTerm)
    }

    /**
     * Save both raw API dump and converted legacy-compatible JSON file locally.
     * This does NOT affect the normal Firebase app flow. It is only for exporting data.
     */
    fun saveLegacyCompatibleToLocalFiles(
        context: Context,
        data: List<String>,
        rawFileName: String = "courses_raw.json",
        legacyFileName: String = "courses.json",
        defaultTerm: String = "1261"
    ) {
        try {
            val rawFlatJsonArray = JSONArray()

            data.forEach { response ->
                val arr = JSONArray(response)
                for (i in 0 until arr.length()) {
                    rawFlatJsonArray.put(arr.getJSONObject(i))
                }
            }

            val rawFile = File(context.filesDir, rawFileName)
            rawFile.writeText(rawFlatJsonArray.toString())

            val legacyJsonArray = convertToLegacyFormat(rawFlatJsonArray, defaultTerm)

            val legacyFile = File(context.filesDir, legacyFileName)
            legacyFile.writeText(legacyJsonArray.toString())

            Log.d(TAG, "Saved ${rawFlatJsonArray.length()} raw records to ${rawFile.absolutePath}")
            Log.d(TAG, "Saved ${legacyJsonArray.length()} converted course records to ${legacyFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save legacy-compatible files", e)
        }
    }

    private fun convertToLegacyFormat(rawFlatJsonArray: JSONArray, defaultTerm: String): JSONArray {
        val groupedByCourseId = linkedMapOf<String, MutableList<JSONObject>>()

        // Group by courseId so each course becomes one document with many sections
        for (i in 0 until rawFlatJsonArray.length()) {
            val obj = rawFlatJsonArray.getJSONObject(i)
            val courseId = obj.optString("courseId", "").ifBlank { "UNKNOWN" }
            groupedByCourseId.getOrPut(courseId) { mutableListOf() }.add(obj)
        }

        val result = JSONArray()

        for ((courseId, items) in groupedByCourseId) {
            val first = items.first()
            val term = first.optString("termCode", defaultTerm)

            val legacyCourse = JSONObject().apply {
                put("subject", "API")
                put("catalog", courseId)
                put("title", "Course $courseId")
                put("term", term)
                put("units", "0.5")
            }

            val sections = JSONArray()

            for (item in items) {
                val classNumber = item.opt("classNumber")?.toString() ?: ""
                val component = item.optString("courseComponent", "")
                val scheduleData = item.optJSONArray("scheduleData")

                // If no scheduleData, still keep a TBA section
                if (scheduleData == null || scheduleData.length() == 0) {
                    sections.put(
                        JSONObject().apply {
                            put("time_date", "")
                            put("location", "TBA")
                            put("component", component)
                            put("class", classNumber)
                        }
                    )
                    continue
                }

                for (j in 0 until scheduleData.length()) {
                    val sched = scheduleData.getJSONObject(j)

                    val startIso =
                        if (sched.isNull("classMeetingStartTime")) "" else sched.optString("classMeetingStartTime", "")
                    val endIso =
                        if (sched.isNull("classMeetingEndTime")) "" else sched.optString("classMeetingEndTime", "")
                    val apiDays = sched.optString("classMeetingDayPatternCode", "")
                    val location = sched.optString("locationName", "").ifBlank { "TBA" }

                    val timeDate = buildLegacyTimeDate(startIso, endIso, apiDays)

                    sections.put(
                        JSONObject().apply {
                            put("time_date", timeDate)
                            put("location", location)
                            put("component", component)
                            put("class", classNumber)
                        }
                    )
                }
            }

            legacyCourse.put("sections", sections)
            result.put(legacyCourse)
        }

        return result
    }

    private fun buildLegacyTimeDate(startIso: String, endIso: String, apiDays: String): String {
        val start = extractTime(startIso)
        val end = extractTime(endIso)
        val days = convertApiDaysToLegacyDays(apiDays)

        if (start.isBlank() || end.isBlank() || days.isBlank()) {
            return ""
        }

        return "$start-$end$days"
    }

    private fun extractTime(iso: String): String {
        if (iso.isBlank()) return ""
        val parts = iso.split("T")
        if (parts.size < 2) return ""
        val timePart = parts[1]
        return if (timePart.length >= 5) timePart.substring(0, 5) else ""
    }

    private fun convertApiDaysToLegacyDays(apiDays: String): String {
        if (apiDays.isBlank()) return ""

        val sb = StringBuilder()
        for (ch in apiDays) {
            when (ch) {
                'M' -> sb.append("M")
                'T' -> sb.append("T")
                'W' -> sb.append("W")
                'R' -> sb.append("Th")
                'F' -> sb.append("F")
            }
        }
        return sb.toString()
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

        var startH = h1.toInt()
        val startM = m1.toInt()
        var endH = h2.toInt()
        val endM = m2.toInt()

        // Waterloo classes are between 8:30 AM and 9:50 PM.
        // If the hour is less than 8, it must be PM (e.g., 4:00 -> 16:00).
        if (startH < 8) startH += 12
        if (endH < 8) endH += 12

        // If the end hour is still less than the start hour, it must be PM.
        // (e.g., 6:30 PM to 9:20 PM -> startH=18, endH=9 -> endH becomes 21)
        if (endH < startH) endH += 12

        val start = Time(startH, startM)
        val end = Time(endH, endM)

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