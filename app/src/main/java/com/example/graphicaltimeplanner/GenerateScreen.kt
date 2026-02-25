package com.example.graphicaltimeplanner

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
    var selectedTerm by remember { mutableStateOf("1261") }
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
    var isLoaded by remember { mutableStateOf(false) }

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
                            Text(text = when(selectedTerm) {
                                "1261" -> "Winter 2026"
                                "1259" -> "Fall 2025"
                                "1255" -> "Spring 2025"
                                else -> selectedTerm
                            }, color = Color.Black)
                        }
                        DropdownMenu(
                            expanded = termExpanded,
                            onDismissRequest = { termExpanded = false }
                        ) {
                            listOf(
                                "1261" to "Winter 2026",
                                "1259" to "Fall 2025",
                                "1255" to "Spring 2025"
                            ).forEach { (code, label) ->
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
                                        onNavigateToTimetable()
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
                            CourseRepository.saveUserSchedule(otherTermsSchedule + generatedSchedules[currentScheduleIndex])
                            onNavigateToTimetable()
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
            if (!current.contains(courseCodes[i])) {
                getCombs(i + 1, current + listOf(courseCodes[i]))
            }
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
            val groupedByComp = sections.groupBy { it.section.component.split(" ").firstOrNull() ?: "" }
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
                    "${it.code}-${it.section.component.split(" ").firstOrNull()}-${it.section.days.joinToString("")}-${it.section.startTime}-${it.section.endTime}" 
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
