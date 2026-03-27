// PreferenceScreen.kt
package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ─── Data model for Preferences wishlist ───────────────────────────────────────────────

data class CourseWishItem(
    val code: String,
    val title: String,
    val units: String,
    val sections: List<Section>
)

// ─── AIScreen ─────────────────────────────────────────────────────────────────

@Composable
fun AIScreen(
    onViewProfile: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCourses: () -> Unit = {},
    onNavigateToChatbot: () -> Unit = {},
    onNavigateToAdvisor: () -> Unit = {}
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFF5F5F5)
    val coroutineScope = rememberCoroutineScope()

    val displayName by AppState.displayName
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    // ── Scheduling preferences ────────────────────────────────────────────────
    var avoidEarlyClasses by remember { mutableStateOf(false) }
    var minimizeGaps       by remember { mutableStateOf(false) }
    val clusterDays = remember { mutableStateMapOf("Mon" to false, "Tue" to false, "Wed" to false, "Thu" to false, "Fri" to false) }
    var maxDailyHours by remember { mutableStateOf(8f) }   // 4..12

    // ── Course selection ──────────────────────────────────────────────────────
    var selectedSubject by remember { mutableStateOf("CS") }
    var searchQuery     by remember { mutableStateOf("") }
    var courses         by remember { mutableStateOf(listOf<Course>()) }
    var isLoading       by remember { mutableStateOf(true) }
    val selectedCourseCodes = remember { mutableStateSetOf<String>() }

    // ── Generate state ────────────────────────────────────────────────────────
    var isGenerating           by remember { mutableStateOf(false) }
    var showNoResultsDialog    by remember { mutableStateOf(false) }
    var showAppliedDialog      by remember { mutableStateOf(false) }
    var generatedSchedules     by remember { mutableStateOf<List<List<Course>>>(emptyList()) }
    var currentScheduleIndex   by remember { mutableStateOf(0) }
    var showScheduleDialog     by remember { mutableStateOf(false) }

    // Resolve which subject to fetch:
    //  - If the search box is blank → use the selected tab (selectedSubject)
    //  - If the search box has text → try to parse a subject prefix from it
    //    (e.g. "CS 446" → "CS", "MATH 135" → "MATH").  If one is found, fetch
    //    that subject regardless of which tab is active.  If no prefix matches,
    //    fall back to the selected tab so the list is never empty.
    //
    // This means the search bar fully overrides the tab filter when the user
    // explicitly types a subject — the tab is only a convenience for browsing.
    val subjectFromQuery = remember(searchQuery) {
        val q = searchQuery.trim().uppercase()
        if (q.isEmpty()) null
        else CourseRepository.ALL_SUBJECTS
            .sortedByDescending { it.length }          // longest match wins
            .firstOrNull { subj ->
                q == subj ||
                        q.startsWith("$subj ") ||
                        q.startsWith("$subj	")
            }
    }

    // The subject we actually fetch from Firestore.
    val fetchSubject = if (!subjectFromQuery.isNullOrEmpty()) subjectFromQuery else selectedSubject

    LaunchedEffect(fetchSubject) {
        isLoading = true
        try {
            courses = CourseRepository.getCourses(term = "1259", subject = fetchSubject)
        } catch (_: Exception) {
            courses = emptyList()
        } finally {
            isLoading = false
        }
    }

    // Group courses by code for display
    val groupedCourses = remember(courses) {
        courses.groupBy { it.code }.map { (code, secs) ->
            val first = secs.first()
            CourseWishItem(
                code = code,
                title = first.title,
                units = first.units,
                sections = secs.map { it.section }
            )
        }
    }

    val filteredGroups = remember(groupedCourses, searchQuery) {
        if (searchQuery.isBlank()) groupedCourses
        else {
            val q = searchQuery.trim().lowercase()
            // Strip any leading subject prefix from q so that "cs 446" or
            // "CS 446" reduces to "446" for catalog-number matching.
            val catalogQuery = q.substringAfter(" ").trim().ifEmpty { q }
            groupedCourses.filter { group ->
                val codeLower  = group.code.lowercase()
                val titleLower = group.title.lowercase()
                // The catalog number is the part after the subject prefix, e.g.
                // "446" from "CS 446".
                val catalogNumber = codeLower.substringAfter(" ").trim()
                codeLower.contains(q) ||          // full code match "cs 446"
                        titleLower.contains(q) ||      // title match
                        catalogNumber.startsWith(catalogQuery) // "446" → "CS 446"
            }
        }
    }

    // Subject chips
    val subjects = CourseRepository.ALL_SUBJECTS

    // ── Generate optimal timetable logic ──────────────────────────────────────
    fun generateTimetable() {
        if (selectedCourseCodes.isEmpty()) return
        isGenerating = true

        // Build wishlist from selected codes
        val wishlist = courses
            .filter { selectedCourseCodes.contains(it.code) }
            .groupBy { it.code }

        coroutineScope.launch {
            try {
                // Filter based on preferences
                val filteredWishlist = wishlist.mapValues { (_, secs) ->
                    secs.filter { course ->
                        val start = course.section.startTime.toFloat()

                        // Avoid early classes (before 9 AM)
                        val passEarly = !avoidEarlyClasses || start >= 9f

                        // Cluster days filter
                        val preferredDays = clusterDays.filter { it.value }.keys
                        val passCluster = preferredDays.isEmpty() ||
                                course.section.days.any { it in preferredDays }

                        passEarly && passCluster
                    }
                }.filter { it.value.isNotEmpty() }

                // Simple conflict-free schedule generation
                val courseList = filteredWishlist.values.map { it }
                val results = mutableListOf<List<Course>>()

                fun hasConflict(schedule: List<Course>, candidate: Course): Boolean {
                    return schedule.any { existing ->
                        val sharedDays = existing.section.days.intersect(candidate.section.days.toSet())
                        sharedDays.isNotEmpty() &&
                                existing.section.startTime < candidate.section.endTime &&
                                candidate.section.startTime < existing.section.endTime
                    }
                }

                fun sectionDuration(course: Course): Float {
                    return course.section.endTime.toFloat() - course.section.startTime.toFloat()
                }

                fun scheduleWithinDailyHours(schedule: List<Course>): Boolean {
                    val dailyHours = mutableMapOf<String, Float>()

                    for (course in schedule) {
                        val duration = sectionDuration(course)
                        for (day in course.section.days) {
                            dailyHours[day] = (dailyHours[day] ?: 0f) + duration
                        }
                    }

                    return dailyHours.values.all { it <= maxDailyHours }
                }

                fun exceedsDailyHours(schedule: List<Course>, candidate: Course): Boolean {
                    val dailyHours = mutableMapOf<String, Float>()

                    for (course in schedule) {
                        val duration = sectionDuration(course)
                        for (day in course.section.days) {
                            dailyHours[day] = (dailyHours[day] ?: 0f) + duration
                        }
                    }

                    val candidateDuration = sectionDuration(candidate)
                    for (day in candidate.section.days) {
                        val newTotal = (dailyHours[day] ?: 0f) + candidateDuration
                        if (newTotal > maxDailyHours) return true
                    }

                    return false
                }

                fun generate(idx: Int, current: List<Course>) {
                    if (results.size >= 5) return

                    if (idx == courseList.size) {
                        if (current.isNotEmpty() && scheduleWithinDailyHours(current)) {
                            results.add(current)
                        }
                        return
                    }

                    for (section in courseList[idx]) {
                        if (!hasConflict(current, section) && !exceedsDailyHours(current, section)) {
                            generate(idx + 1, current + section)
                        }
                    }

                    generate(idx + 1, current)
                }

                generate(0, emptyList())

                // Sort by number of courses (more = better)
                val sorted = results.sortedByDescending { schedule ->
                    var score = schedule.size * 100
                    if (minimizeGaps) {
                        // Penalise schedules with large gaps per day
                        val byDay = schedule.flatMap { c -> c.section.days.map { d -> d to c } }
                            .groupBy({ it.first }, { it.second })
                        val gapPenalty = byDay.values.sumOf { daySecs ->
                            if (daySecs.size < 2) 0
                            else {
                                val sorted2 = daySecs.sortedBy { it.section.startTime }
                                sorted2.zipWithNext().sumOf { (a, b) ->
                                    (b.section.startTime.toFloat() - a.section.endTime.toFloat()).toInt()
                                }
                            }
                        }
                        score -= gapPenalty
                    }
                    score
                }

                generatedSchedules = sorted
                currentScheduleIndex = 0
                isGenerating = false

                if (sorted.isEmpty()) showNoResultsDialog = true
                else showScheduleDialog = true

            } catch (_: Exception) {
                isGenerating = false
                showNoResultsDialog = true
            }
        }
    }

    // ── Generated schedule dialog ─────────────────────────────────────────────
    if (showScheduleDialog && generatedSchedules.isNotEmpty()) {
        val schedule = generatedSchedules[currentScheduleIndex]
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = {
                Text(
                    "Schedule ${currentScheduleIndex + 1} of ${generatedSchedules.size}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    schedule.forEach { course ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(course.code, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${course.section.component} · ${course.section.days.joinToString(",")} ${course.section.startTime}–${course.section.endTime}", fontSize = 12.sp, color = Color.Gray)
                                Text(course.section.location, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        if (currentScheduleIndex > 0) {
                            TextButton(onClick = { currentScheduleIndex-- }) { Text("← Prev") }
                        }
                        if (currentScheduleIndex < generatedSchedules.size - 1) {
                            TextButton(onClick = { currentScheduleIndex++ }) { Text("Next →") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppState.scheduledCourses.clear()
                        AppState.scheduledCourses.addAll(schedule)
                        coroutineScope.launch {
                            val activeId = AppState.activeTimetableId.value
                            if (activeId != null) {
                                val idx = AppState.timetables.indexOfFirst { it.id == activeId }
                                if (idx >= 0) {
                                    AppState.timetables[idx] = AppState.timetables[idx].copy(
                                        courses = AppState.scheduledCourses.toList()
                                    )
                                }
                            }
                            CourseRepository.saveAllTimetables(
                                AppState.timetables.toList(),
                                AppState.activeTimetableId.value
                            )
                        }
                        showScheduleDialog = false
                        showAppliedDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black)
                ) { Text("Apply to Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Close") }
            }
        )
    }

    if (showNoResultsDialog) {
        AlertDialog(
            onDismissRequest = { showNoResultsDialog = false },
            title = { Text("No Schedules Found") },
            text = { Text("Could not generate a conflict-free timetable with the selected preferences. Try relaxing some filters or selecting different courses.") },
            confirmButton = { TextButton(onClick = { showNoResultsDialog = false }) { Text("OK") } }
        )
    }

    if (showAppliedDialog) {
        AlertDialog(
            onDismissRequest = { showAppliedDialog = false },
            title = { Text("✅ Schedule Applied") },
            text = { Text("The generated schedule has been saved to your timetable.") },
            confirmButton = {
                Button(
                    onClick = { showAppliedDialog = false; onNavigateToHome() },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black)
                ) { Text("View Timetable") }
            },
            dismissButton = { TextButton(onClick = { showAppliedDialog = false }) { Text("Stay Here") } }
        )
    }

    // ── Main layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBackground)
            .statusBarsPadding()
    ) {
        // Top header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onViewProfile() }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryYellow),
                    contentAlignment = Alignment.Center
                ) {
                    if (nameInitial != null) {
                        Text(nameInitial, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(displayName.ifBlank { "User" }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("View Profile", fontSize = 13.sp, color = Color.Gray)
                }
            }
            TextButton(onClick = onLogout) {
                Text("Logout", color = Color.Gray, fontSize = 14.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {

            // ── Preferences Recommendations Title ───────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = primaryYellow,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scheduling Preferences", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select courses with preferences, and generate an optimized timetable",
                    fontSize = 14.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Avoid Early Classes
            item {
                PreferenceToggleCard(
                    title = "Avoid Early Classes",
                    description = "Prefer classes starting at 9 AM or later",
                    checked = avoidEarlyClasses,
                    onCheckedChange = { avoidEarlyClasses = it },
                    primaryYellow = primaryYellow
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Minimize Gaps
            item {
                PreferenceToggleCard(
                    title = "Minimize Gaps",
                    description = "Reduce time between classes on the same day",
                    checked = minimizeGaps,
                    onCheckedChange = { minimizeGaps = it },
                    primaryYellow = primaryYellow
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Cluster Classes On
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cluster Classes On", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Select days you prefer to have more classes", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Mon", "Tue", "Wed", "Thu", "Fri").forEach { day ->
                                val selected = clusterDays[day] == true
                                OutlinedButton(
                                    onClick = { clusterDays[day] = !selected },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) primaryYellow else Color.Transparent,
                                        contentColor = Color.Black
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) primaryYellow else Color(0xFFCCCCCC)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(day, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Maximum Daily Hours
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Maximum Daily Hours", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Limit total class time per day: ${maxDailyHours.toInt()} hours", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = maxDailyHours,
                            onValueChange = { maxDailyHours = it },
                            valueRange = 4f..12f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = primaryYellow,
                                inactiveTrackColor = Color(0xFFE0E0E0)
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("4h", fontSize = 12.sp, color = Color.Gray)
                            Text("12h", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Generate Button ────────────────────────────────────────
            item {
                Button(
                    onClick = { generateTimetable() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedCourseCodes.isNotEmpty() && !isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryYellow,
                        contentColor = Color.Black,
                        disabledContainerColor = primaryYellow.copy(alpha = 0.5f),
                        disabledContentColor = Color.Black.copy(alpha = 0.5f)
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Optimal Timetable", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Select Courses ─────────────────────────────────────────────
            item {
                Text("Select Courses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Subject chips row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { subj ->
                        val sel = subj == selectedSubject
                        FilterChip(
                            selected = sel,
                            onClick = { selectedSubject = subj },
                            label = { Text(subj, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryYellow,
                                selectedLabelColor = Color.Black
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = sel,
                                selectedBorderColor = primaryYellow,
                                borderColor = Color(0xFFDDDDDD)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search courses...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = primaryYellow,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Course list
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryYellow)
                    }
                }
            } else {
                items(filteredGroups, key = { it.code }) { group ->
                    val isSelected = selectedCourseCodes.contains(group.code)
                    val sectionCount = group.sections.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (isSelected) selectedCourseCodes.remove(group.code)
                                else selectedCourseCodes.add(group.code)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFFF9E6) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) selectedCourseCodes.add(group.code)
                                    else selectedCourseCodes.remove(group.code)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = primaryYellow,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = Color.LightGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${group.code} - ${group.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${group.units} units • $sectionCount sections available", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

        }

        // Bottom nav
        BottomNavBar(
            selectedItem = BottomNavItem.PREFERENCE,
            onCoursesClick = onNavigateToCourses,
            onAiClick = {},
            onScheduleClick = onNavigateToHome,
            onChatbotClick = onNavigateToChatbot,
            onAdvisorClick = onNavigateToAdvisor
        )
    }
}

// ── Reusable preference toggle card ──────────────────────────────────────────

@Composable
private fun PreferenceToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryYellow: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(description, fontSize = 13.sp, color = Color.Gray)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = primaryYellow,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray
                )
            )
        }
    }
}