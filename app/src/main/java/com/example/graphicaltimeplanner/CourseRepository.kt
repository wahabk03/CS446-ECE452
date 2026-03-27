package com.example.graphicaltimeplanner

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val TAG = "CourseRepo"

// Singleton Repository
object CourseRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private var advisorsCache: List<Advisor> = emptyList()

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
                val units = document.getString("units") ?: ""

                // 'sections' is an array of maps
                val rawSections = document.get("sections") as? List<Map<String, Any>> ?: emptyList()
                Log.d(TAG, "  Doc ${document.id}: ${rawSections.size} sections")

                for (secMap in rawSections) {
                    val timeDateStr = secMap["time_date"] as? String ?: ""
                    val location = secMap["location"] as? String ?: ""
                    val component = secMap["component"] as? String ?: ""
                    val classNum = secMap["class"] as? String ?: ""

                    // Skip invalid sections that have no class number or component
                    // (These are usually orphaned additional meeting times from bad scraping)
                    if (classNum.isBlank() && component.isBlank()) {
                        continue
                    }

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
                                term = term,
                                units = units
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
                                term = term,
                                units = units
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
        code = map["code"]?.toString() ?: "",
        title = map["title"]?.toString() ?: "",
        term = map["term"]?.toString() ?: "",
        units = map["units"]?.toString() ?: "",
        section = Section(
            classNumber = map["classNumber"]?.toString() ?: "",
            component = map["component"]?.toString() ?: "",
            days = (map["days"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            startTime = Time(
                (map["startHour"] as? Number)?.toInt() ?: 0,
                (map["startMinute"] as? Number)?.toInt() ?: 0
            ),
            endTime = Time(
                (map["endHour"] as? Number)?.toInt() ?: 0,
                (map["endMinute"] as? Number)?.toInt() ?: 0
            ),
            location = map["location"]?.toString() ?: ""
        )
    )

    // ─── Profile helpers ──────────────────────────────────────────────────────

    /** Persist the user's display name to Firestore users/{uid}.displayName */
    suspend fun saveUserProfile(displayName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .update("displayName", displayName)
                .await()
        } catch (e: Exception) {
            // Document may not exist yet – use set with merge
            try {
                db.collection("users").document(userId)
                    .set(mapOf("displayName" to displayName), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            } catch (e2: Exception) {
                Log.e(TAG, "Error saving user profile", e2)
            }
        }
    }

    /** Load the user's display name from Firestore. Returns null if not found. */
    suspend fun loadUserProfile(): String? {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return try {
            val doc = db.collection("users").document(userId).get().await()
            doc.getString("displayName")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile", e)
            null
        }
    }

    /** Merge arbitrary profile fields under users/{uid}. */
    suspend fun saveUserExtendedProfile(fields: Map<String, Any>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            db.collection("users").document(userId)
                .set(fields, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving extended profile", e)
        }
    }

    /** Read all profile fields from users/{uid}. */
    suspend fun getUserExtendedProfile(): Map<String, Any> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyMap()
        return try {
            val doc = db.collection("users").document(userId).get().await()
            doc.data ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading extended profile", e)
            emptyMap()
        }
    }

    /** Load available academic programs from Firestore collection `programs`. */
    suspend fun getPrograms(): List<Program> {
        return try {
            val snapshot = db.collection("programs").get().await()
            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val faculty = doc.getString("faculty") ?: ""
                val degreeType = doc.getString("degreeType")
                    ?: doc.getString("degree_type")
                    ?: ""
                Program(
                    slug = doc.id,
                    name = name,
                    faculty = faculty,
                    degreeType = degreeType
                )
            }.sortedBy { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading programs", e)
            emptyList()
        }
    }

    /**
     * Load advisor mappings from Firestore collection `advisors`.
     * Expected fields: programSlug/program_slug, email, name, yearLevel/year_level, isFallback/is_fallback, faculty.
     */
    suspend fun getAdvisors(forceRefresh: Boolean = false): List<Advisor> {
        if (advisorsCache.isNotEmpty() && !forceRefresh) return advisorsCache

        advisorsCache = try {
            val snapshot = db.collection("advisors").get().await()
            snapshot.documents.mapNotNull { doc ->
                val programSlug = doc.getString("programSlug")
                    ?: doc.getString("program_slug")
                    ?: ""
                val email = doc.getString("email") ?: return@mapNotNull null
                val name = doc.getString("name") ?: "Academic Advisor"
                val yearLevel = doc.getString("yearLevel")
                    ?: doc.getString("year_level")
                    ?: "all"
                val isFallback = (doc.getBoolean("isFallback")
                    ?: doc.getBoolean("is_fallback")
                    ?: false)
                val faculty = doc.getString("faculty") ?: ""

                Advisor(
                    programSlug = if (programSlug.isBlank()) doc.id else programSlug,
                    email = email,
                    name = name,
                    yearLevel = yearLevel,
                    isFallback = isFallback,
                    faculty = faculty
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading advisors", e)
            emptyList()
        }

        return advisorsCache
    }

    /** Resolve best advisor match for the user's program/year with faculty fallback. */
    fun getAdvisorForProgram(programSlug: String, isFirstYear: Boolean, faculty: String): Advisor? {
        val advisors = advisorsCache
        if (advisors.isEmpty()) return null

        val desiredYear = if (isFirstYear) "first-year" else "upper-year"

        val exactByYear = advisors.firstOrNull {
            !it.isFallback && it.programSlug.equals(programSlug, ignoreCase = true) &&
                it.yearLevel.equals(desiredYear, ignoreCase = true)
        }
        if (exactByYear != null) return exactByYear

        val exactAllYears = advisors.firstOrNull {
            !it.isFallback && it.programSlug.equals(programSlug, ignoreCase = true) &&
                it.yearLevel.equals("all", ignoreCase = true)
        }
        if (exactAllYears != null) return exactAllYears

        val fallbackByYear = advisors.firstOrNull {
            it.isFallback && it.faculty.equals(faculty, ignoreCase = true) &&
                it.yearLevel.equals(desiredYear, ignoreCase = true)
        }
        if (fallbackByYear != null) return fallbackByYear

        return advisors.firstOrNull {
            it.isFallback && it.faculty.equals(faculty, ignoreCase = true) &&
                it.yearLevel.equals("all", ignoreCase = true)
        }
    }

    // ─── Schedule helpers ─────────────────────────────────────────────────────

    // ── Multi-timetable helpers ───────────────────────────────────────────────

    suspend fun saveAllTimetables(timetables: List<Timetable>, activeTimetableId: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val data = mapOf(
                "timetables" to timetables.map { tt ->
                    mapOf(
                        "id" to tt.id,
                        "name" to tt.name,
                        "term" to tt.term,
                        "courses" to tt.courses.map { courseToMap(it) }
                    )
                },
                "activeTimetableId" to (activeTimetableId ?: "")
            )
            db.collection("users").document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving all timetables", e)
        }
    }

    suspend fun loadAllTimetables(): Pair<List<Timetable>, String?> {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Pair(emptyList(), null)
        return try {
            val doc = db.collection("users").document(userId).get().await()
            val rawList = doc.get("timetables") as? List<Map<String, Any>> ?: emptyList()
            val timetables = rawList.map { ttMap ->
                val id = ttMap["id"] as? String ?: java.util.UUID.randomUUID().toString()
                val name = ttMap["name"] as? String ?: "Timetable"
                val term = ttMap["term"] as? String ?: "Winter 2026"
                val rawCourses = ttMap["courses"] as? List<Map<String, Any>> ?: emptyList()
                Timetable(id = id, name = name, term = term, courses = rawCourses.map { mapToCourse(it) })
            }
            val activeId = doc.getString("activeTimetableId")?.takeIf { it.isNotBlank() }
            Pair(timetables, activeId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all timetables", e)
            Pair(emptyList(), null)
        }
    }

    suspend fun saveUserSchedule(courses: List<Course>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Log.d(TAG, "Saving ${courses.size} courses for user $userId")

        try {
            val data = mapOf("scheduledCourses" to courses.map { courseToMap(it) })
            // Merge to avoid wiping newer fields like timetables/activeTimetableId.
            db.collection("users").document(userId)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
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
