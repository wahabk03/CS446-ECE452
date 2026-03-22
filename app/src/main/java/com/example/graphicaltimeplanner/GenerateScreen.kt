package com.example.graphicaltimeplanner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    onBack: () -> Unit,
    onNavigateToTimetable: () -> Unit
) {
    var selectedTerm by remember { mutableStateOf(CourseRepository.TERM_MAPPINGS.first().first) }
    var selectedSubject by remember { mutableStateOf("CS") }
    var searchQuery by remember { mutableStateOf("") }
    var termExpanded by remember { mutableStateOf(false) }

    // Wishlist: course code -> list of all its sections
    var wishlist by remember { mutableStateOf(mapOf<String, List<Course>>()) }
    
    var enforceAll by remember { mutableStateOf(true) }
    var subsetSize by remember { mutableStateOf(3f) }
    
    var generatedSchedules by remember { mutableStateOf<List<List<Course>>>(emptyList()) }
    var currentScheduleIndex by remember { mutableStateOf(0) }
    var isGenerating by remember { mutableStateOf(false) }
    var showNoResultsDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showEmailAdvisorDialog by remember { mutableStateOf(false) }
    var savedScheduleForEmail by remember { mutableStateOf<List<Course>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Load saved wishlist and schedules when term changes
    LaunchedEffect(selectedTerm) {
        isLoaded = false
        val (savedWishlist, savedSchedules) = CourseRepository.loadGenerateState(selectedTerm)
        wishlist = savedWishlist
        generatedSchedules = savedSchedules
        currentScheduleIndex = 0
        isLoaded = true
    }

    // Save state whenever wishlist or generatedSchedules changes
    val scope = rememberCoroutineScope()
    LaunchedEffect(wishlist, generatedSchedules) {
        if (isLoaded) {
            scope.launch {
                CourseRepository.saveGenerateState(selectedTerm, wishlist, generatedSchedules)
            }
        }
    }

    // Ensure subsetSize doesn't exceed wishlist size
    LaunchedEffect(wishlist) {
        if (wishlist.isNotEmpty() && subsetSize > wishlist.size) {
            subsetSize = wishlist.size.toFloat()
        }
    }

    // Fetch available courses for the selected subject
    var availableCourses by remember { mutableStateOf(emptyList<Course>()) }
    var isLoadingCourses by remember { mutableStateOf(false) }
    
    LaunchedEffect(selectedTerm, selectedSubject) {
        isLoadingCourses = true
        try {
            availableCourses = CourseRepository.getCourses(selectedTerm, selectedSubject)
        } catch (e: Exception) {
            availableCourses = emptyList()
        }
        isLoadingCourses = false
    }

    val groupedAvailable = remember(availableCourses, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            availableCourses
        } else {
            val query = searchQuery.replace(" ", "").lowercase()
            availableCourses.filter { 
                it.code.replace(" ", "").lowercase().contains(query) || 
                it.title.lowercase().contains(searchQuery.lowercase())
            }
        }
        filtered.groupBy { it.code }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.1f))
    ) {
        // Top Decorative Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(colorResource(R.color.uw_gold_lvl4))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assistant",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Back", color = Color.White)
                    }

                    // Term Selector Dropdown
                    Box {
                        Button(
                            onClick = { termExpanded = !termExpanded },
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4))
                        ) {
                            val termLabel = CourseRepository.TERM_MAPPINGS.find { it.first == selectedTerm }?.second ?: selectedTerm
                            Text(text = termLabel, color = Color.Black)
                        }
                        DropdownMenu(
                            expanded = termExpanded,
                            onDismissRequest = { termExpanded = false }
                        ) {
                            CourseRepository.TERM_MAPPINGS.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        if (selectedTerm != code) {
                                            selectedTerm = code
                                        }
                                        termExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (generatedSchedules.isNotEmpty()) {
                // --- Generated Timetable View ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { 
                            generatedSchedules = emptyList()
                            currentScheduleIndex = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                    ) {
                        Text("Edit Wishlist")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (currentScheduleIndex > 0) currentScheduleIndex-- },
                            enabled = currentScheduleIndex > 0
                        ) {
                            Text("<", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${currentScheduleIndex + 1} / ${generatedSchedules.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { if (currentScheduleIndex < generatedSchedules.size - 1) currentScheduleIndex++ },
                            enabled = currentScheduleIndex < generatedSchedules.size - 1
                        ) {
                            Text(">", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    val currentSchedule = generatedSchedules[currentScheduleIndex]
                    // Assign colors
                    val courseColors = remember(currentSchedule) {
                        val colors = mutableMapOf<String, Color>()
                        val usedColors = mutableSetOf<Color>()
                        currentSchedule.forEach { course ->
                            if (!colors.containsKey(course.code)) {
                                val available = COURSE_COLORS - usedColors
                                val color = available.firstOrNull() ?: COURSE_COLORS.random()
                                colors[course.code] = color
                                usedColors.add(color)
                            }
                        }
                        colors
                    }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        TimetableView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            courses = currentSchedule,
                            courseColors = courseColors,
                            onClearAll = { },
                            onRemoveCourse = { }
                        )
                        
                        val onlineCourses = currentSchedule.filter { it.section.days.isEmpty() }
                        if (onlineCourses.isNotEmpty()) {
                            val uniqueOnlineCodes = onlineCourses.map { it.code }.toSet()
                            Text(
                                text = "Online/TBA Courses: ${uniqueOnlineCodes.joinToString(", ")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    val existingSchedule = CourseRepository.loadUserSchedule()
                                    val existingScheduleForTerm = existingSchedule.filter { it.term == selectedTerm }
                                    if (existingScheduleForTerm.isNotEmpty()) {
                                        showOverwriteDialog = true
                                    } else {
                                        val otherTermsSchedule = existingSchedule.filter { it.term != selectedTerm }
                                        CourseRepository.saveUserSchedule(otherTermsSchedule + currentSchedule)
                                        savedScheduleForEmail = currentSchedule
                                        showEmailAdvisorDialog = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4))
                        ) {
                            Text("Export to My Timetable", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // --- Configuration View ---
                
                // Wishlist Display
                if (wishlist.isNotEmpty()) {
                    Text("Wishlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(wishlist.keys.toList()) { code ->
                            InputChip(
                                selected = true,
                                onClick = { wishlist = wishlist - code },
                                label = { Text(code) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = colorResource(R.color.uw_gold_lvl4)
                                )
                            )
                        }
                    }
                }

                // Settings & Generate Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = enforceAll,
                                onCheckedChange = { enforceAll = it }
                            )
                            Text("Enforce all courses in wishlist")
                        }
                        
                        if (!enforceAll && wishlist.isNotEmpty()) {
                            Text("Select subset size: ${subsetSize.toInt()}", modifier = Modifier.padding(top = 8.dp))
                            Slider(
                                value = subsetSize,
                                onValueChange = { subsetSize = it },
                                valueRange = 1f..(wishlist.size.toFloat().coerceAtLeast(1f)),
                                steps = (wishlist.size - 2).coerceAtLeast(0)
                            )
                        }
                        
                        Button(
                            onClick = {
                                isGenerating = true
                                val results = generateTimetables(
                                    wishlist = wishlist,
                                    enforceAll = enforceAll,
                                    subsetSize = subsetSize.toInt()
                                )
                                isGenerating = false
                                if (results.isEmpty()) {
                                    generatedSchedules = emptyList() // Clear any stale schedules
                                    showNoResultsDialog = true
                                } else {
                                    generatedSchedules = results
                                    currentScheduleIndex = 0
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4)),
                            enabled = wishlist.isNotEmpty() && !isGenerating
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                            } else {
                                Text("Generate", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Add Courses to Wishlist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))

                // Subject Selector
                SubjectSelector(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { 
                        searchQuery = it 
                        val letters = it.takeWhile { char -> char.isLetter() }.uppercase()
                        if (CourseRepository.ALL_SUBJECTS.contains(letters)) {
                            selectedSubject = letters
                        }
                    },
                    selectedSubject = selectedSubject,
                    onSubjectSelected = { selectedSubject = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Available Courses List
                if (isLoadingCourses) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorResource(R.color.uw_gold_lvl4))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groupedAvailable.keys.toList()) { code ->
                            val sections = groupedAvailable[code] ?: emptyList()
                            val title = sections.firstOrNull()?.title ?: ""
                            val inWishlist = wishlist.containsKey(code)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (inWishlist) {
                                            wishlist = wishlist - code
                                        } else {
                                            wishlist = wishlist + (code to sections)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (inWishlist) colorResource(R.color.uw_gold_lvl4).copy(alpha = 0.3f) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = code, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(text = title, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    if (inWishlist) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    } else {
                                        Text("+ Add", color = colorResource(R.color.uw_gold_lvl4), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoResultsDialog) {
        AlertDialog(
            onDismissRequest = { showNoResultsDialog = false },
            title = { Text("No Timetable Found") },
            text = { Text("Could not find a conflict-free timetable with the selected courses and settings. Try reducing the subset size or removing some courses.") },
            confirmButton = {
                Button(onClick = { showNoResultsDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("Overwrite Existing Timetable?") },
            text = { Text("You already have courses saved in your timetable for this semester. Exporting this generated schedule will overwrite your current timetable for this semester. Do you want to proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteDialog = false
                        scope.launch {
                            val existingSchedule = CourseRepository.loadUserSchedule()
                            val otherTermsSchedule = existingSchedule.filter { it.term != selectedTerm }
                            val currentSchedule = generatedSchedules[currentScheduleIndex]
                            CourseRepository.saveUserSchedule(otherTermsSchedule + currentSchedule)
                            savedScheduleForEmail = currentSchedule
                            showEmailAdvisorDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showOverwriteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Email advisor dialog
    if (showEmailAdvisorDialog) {
        LaunchedEffect(Unit) {
            CourseRepository.getAdvisors() // Ensure advisors are cached
        }

        val termName = CourseRepository.TERM_MAPPINGS.find { it.first == selectedTerm }?.second ?: selectedTerm

        AlertDialog(
            onDismissRequest = {
                showEmailAdvisorDialog = false
                onNavigateToTimetable()
            },
            title = { Text("Notify Academic Advisor?") },
            text = {
                Text("Your timetable has been saved. Would you like to email your academic advisor about your new schedule?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmailAdvisorDialog = false
                        scope.launch {
                            val (programSlug, faculty, yearLevel) = CourseRepository.getUserProfile()
                            if (programSlug != null && faculty != null) {
                                val isFirstYear = (yearLevel ?: 1) <= 1
                                val advisor = CourseRepository.getAdvisorForProgram(programSlug, isFirstYear, faculty)
                                if (advisor != null) {
                                    val body = buildScheduleEmailBody(savedScheduleForEmail, termName)
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf(advisor.email))
                                        putExtra(Intent.EXTRA_SUBJECT, "Timetable Update - $termName")
                                        putExtra(Intent.EXTRA_TEXT, body)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "No advisor on file for your program", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Set your program in Profile first", Toast.LENGTH_SHORT).show()
                            }
                            onNavigateToTimetable()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.uw_gold_lvl4))
                ) {
                    Text("Send Email", color = Color.Black)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showEmailAdvisorDialog = false
                        onNavigateToTimetable()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Skip")
                }
            }
        )
    }
}

private fun buildScheduleEmailBody(courses: List<Course>, termName: String): String {
    val sb = StringBuilder()
    sb.appendLine("Hi,")
    sb.appendLine()
    sb.appendLine("I have updated my timetable for $termName. Here is my schedule:")
    sb.appendLine()

    val grouped = courses.groupBy { it.code }
    for ((code, sections) in grouped.toSortedMap()) {
        val title = sections.first().title
        sb.appendLine("$code - $title")
        for (course in sections) {
            val days = course.section.days.joinToString(", ")
            val time = if (course.section.startTime.hour == 0 && course.section.endTime.hour == 0) "TBA"
                       else "${course.section.startTime} - ${course.section.endTime}"
            sb.appendLine("  ${course.section.component} | $days $time | ${course.section.location}")
        }
        sb.appendLine()
    }

    sb.appendLine("Thank you.")
    return sb.toString()
}

fun generateTimetables(
    wishlist: Map<String, List<Course>>,
    enforceAll: Boolean,
    subsetSize: Int
): List<List<Course>> {
    val results = mutableListOf<List<Course>>()
    val courseCodes = wishlist.keys.toList()
    if (courseCodes.isEmpty()) return emptyList()

    val targetSize = if (enforceAll) courseCodes.size else subsetSize.coerceAtMost(courseCodes.size)
    
    // 1. Generate combinations of course codes of size `targetSize`
    val combinations = mutableListOf<List<String>>()
    fun getCombs(start: Int, current: List<String>) {
        if (current.size == targetSize) {
            combinations.add(current)
            return
        }
        for (i in start until courseCodes.size) {
            getCombs(i + 1, current + listOf(courseCodes[i]))
        }
    }
    getCombs(0, emptyList())

    // 2. For each combination, find valid schedules
    val seenSignatures = mutableSetOf<String>()
    
    for (comb in combinations) {
        if (results.size >= 5) break
        
        // For each course in comb, we need exactly one section per component
        val componentChoices = mutableListOf<List<Course>>()
        var validCombination = true
        for (code in comb) {
            val sections = wishlist[code] ?: emptyList()
            if (sections.isEmpty()) {
                validCombination = false
                break
            }
            val groupedByComp = sections.groupBy { it.section.componentType }
            for ((_, compSections) in groupedByComp) {
                if (compSections.isNotEmpty()) {
                    componentChoices.add(compSections)
                }
            }
        }
        
        if (!validCombination) continue
        
        // DFS to pick one from each componentChoice
        fun dfs(index: Int, currentSchedule: List<Course>) {
            if (results.size >= 5) return
            if (index == componentChoices.size) {
                // Maintain a counter of unique courses scheduled to ensure target number is reached
                val scheduledCourseCodes = currentSchedule.map { it.code }.toSet()
                if (scheduledCourseCodes.size != targetSize) {
                    return // Reject if the number of scheduled courses doesn't match the target
                }

                // We successfully scheduled exactly one section for every required component of every course in the combination.
                // Create a visual signature to prevent identical-looking schedules
                val signature = currentSchedule.map { 
                    "${it.code}-${it.section.componentType}-${it.section.days.joinToString("")}-${it.section.startTime}-${it.section.endTime}" 
                }.sorted().joinToString("|")
                
                if (signature !in seenSignatures) {
                    seenSignatures.add(signature)
                    results.add(currentSchedule)
                }
                return
            }
            
            for (course in componentChoices[index]) {
                // Check conflict
                var conflict = false
                for (scheduled in currentSchedule) {
                    val daysIntersect = course.section.days.intersect(scheduled.section.days.toSet())
                    if (daysIntersect.isNotEmpty()) {
                        if (course.section.startTime < scheduled.section.endTime && scheduled.section.startTime < course.section.endTime) {
                            conflict = true
                            break
                        }
                    }
                }
                if (!conflict) {
                    dfs(index + 1, currentSchedule + listOf(course))
                }
            }
        }
        
        dfs(0, emptyList())
    }
    
    return results
}
