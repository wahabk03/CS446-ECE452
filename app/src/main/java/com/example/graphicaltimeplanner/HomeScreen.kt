// HomeScreen.kt
package com.example.graphicaltimeplanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

// ─── iCal export helper ───────────────────────────────────────────────────────

private fun buildIcsContent(courses: List<Course>): String {
    val sb = StringBuilder()
    sb.appendLine("BEGIN:VCALENDAR")
    sb.appendLine("VERSION:2.0")
    sb.appendLine("PRODID:-//GraphicalTimePlanner//EN")
    sb.appendLine("CALSCALE:GREGORIAN")
    sb.appendLine("METHOD:PUBLISH")

    val dayToIcal = mapOf(
        "Mon" to "MO", "Tue" to "TU", "Wed" to "WE",
        "Thu" to "TH", "Fri" to "FR", "Sat" to "SA", "Sun" to "SU"
    )

    // Use a fixed reference week (first week of term) – Mon 2026-01-05
    val refMonday = mapOf(
        "Mon" to "20260105", "Tue" to "20260106", "Wed" to "20260107",
        "Thu" to "20260108", "Fri" to "20260109"
    )

    for (course in courses) {
        val s = course.section
        for (day in s.days) {
            val dateStr = refMonday[day] ?: continue
            val icalDays = s.days.mapNotNull { dayToIcal[it] }.joinToString(",")
            val uid = UUID.randomUUID().toString()
            val startStr = "%sT%02d%02d00".format(dateStr, s.startTime.hour, s.startTime.minute)
            val endStr   = "%sT%02d%02d00".format(dateStr, s.endTime.hour,   s.endTime.minute)
            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:$uid")
            sb.appendLine("DTSTART:$startStr")
            sb.appendLine("DTEND:$endStr")
            sb.appendLine("RRULE:FREQ=WEEKLY;BYDAY=$icalDays;COUNT=13")
            sb.appendLine("SUMMARY:${course.code} – ${s.component}")
            sb.appendLine("LOCATION:${s.location}")
            sb.appendLine("DESCRIPTION:${course.title}")
            sb.appendLine("END:VEVENT")
            break  // one VEVENT per course (RRULE covers all days)
        }
    }

    sb.appendLine("END:VCALENDAR")
    return sb.toString()
}

private fun saveIcsToDownloads(context: Context, icsContent: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "timetable.ics")
            put(MediaStore.Downloads.MIME_TYPE, "text/calendar")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(icsContent.toByteArray()) }
        true
    } catch (e: Exception) {
        false
    }
}

// ─── Timetable-to-Bitmap helper ───────────────────────────────────────────────

private val courseColorInts = listOf(
    Color(0xFFFFD700).toArgb(), Color(0xFFE1BEE7).toArgb(), Color(0xFFBBDEFB).toArgb(),
    Color(0xFFC8E6C9).toArgb(), Color(0xFFFFF9C4).toArgb()
)

private fun renderTimetableBitmap(courses: List<Course>): Bitmap {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 22
    val hourPx = 80f
    val timeColW = 80f
    val headerH = 50f
    val colW = 160f
    val totalW = (timeColW + colW * days.size).toInt()
    val totalH = (headerH + (endHour - startHour + 1) * hourPx).toInt()

    val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)

    val gridPaint = Paint().apply { color = android.graphics.Color.parseColor("#E0E0E0"); strokeWidth = 1f }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#888888")
        textSize = 28f
    }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFD700")
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }
    val blockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
    }
    val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#444444"); textSize = 20f
    }

    // Day headers
    days.forEachIndexed { i, day ->
        val x = timeColW + i * colW + colW / 2f
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(day, x, headerH - 10f, boldPaint)
        canvas.drawLine(timeColW + i * colW, 0f, timeColW + i * colW, totalH.toFloat(), gridPaint)
    }
    canvas.drawLine(timeColW + days.size * colW, 0f, timeColW + days.size * colW, totalH.toFloat(), gridPaint)
    canvas.drawLine(0f, headerH, totalW.toFloat(), headerH, gridPaint)

    // Hour lines + labels
    for (h in startHour..endHour) {
        val y = headerH + (h - startHour) * hourPx
        canvas.drawLine(0f, y, totalW.toFloat(), y, gridPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("%d:00".format(h), timeColW - 6f, y + 20f, textPaint)
    }

    // Course color map
    val colorMap = mutableMapOf<String, Int>()
    courses.distinctBy { it.code }.forEachIndexed { i, c ->
        colorMap[c.code] = courseColorInts[i % courseColorInts.size]
    }

    val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (course in courses) {
        val color = colorMap[course.code] ?: courseColorInts[0]
        blockPaint.color = color
        for (day in course.section.days) {
            val di = days.indexOf(day)
            if (di < 0) continue
            val startF = course.section.startTime.toFloat()
            val endF = course.section.endTime.toFloat()
            if (endF <= startF) continue
            val left = timeColW + di * colW + 4f
            val top = headerH + (startF - startHour) * hourPx + 2f
            val right = left + colW - 8f
            val bottom = headerH + (endF - startHour) * hourPx - 2f
            canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, blockPaint)
            blockTextPaint.textAlign = Paint.Align.CENTER
            val cx = (left + right) / 2f
            canvas.drawText(course.code, cx, top + 30f, blockTextPaint)
            subTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(course.section.component, cx, top + 54f, subTextPaint)
        }
    }
    return bmp
}

private fun saveBitmapToDownloads(context: Context, bmp: Bitmap): Boolean {
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "timetable.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) {
        false
    }
}

// ─── HomeScreen ───────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToTimetable: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    onNavigateToCourses: () -> Unit,
    onNavigateToChatbot: () -> Unit,
    onViewProfile: () -> Unit,
    onNavigateToAdvisor: () -> Unit = {},
    onLogout: () -> Unit
) {
    val primaryYellow = Color(0xFFFFD700)
    val lightBackground = Color(0xFFFDFDFD)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scheduledCourses by rememberUpdatedState(AppState.scheduledCourses.toList())
    val displayName by AppState.displayName
    val nameInitial = displayName.firstOrNull()?.uppercaseChar()?.toString()

    // Export dialog state
    var showExportSheet by remember { mutableStateOf(false) }

    // Timetable dropdown state
    var showTimetableDropdown by remember { mutableStateOf(false) }
    var showAddTimetableDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var newTimetableName by remember { mutableStateOf("") }

    val timetables = AppState.timetables
    val activeTimetableId by AppState.activeTimetableId

    val courseColors = remember(scheduledCourses) {
        val palette = listOf(
            Color(0xFFFFD700), Color(0xFFE1BEE7), Color(0xFFBBDEFB),
            Color(0xFFC8E6C9), Color(0xFFFFF9C4)
        )
        val map = mutableMapOf<String, Color>()
        scheduledCourses.distinctBy { it.code }.forEachIndexed { i, c ->
            map[c.code] = palette[i % palette.size]
        }
        map
    }

    // Load all timetables from Firebase on first open
    LaunchedEffect(Unit) {
        val (loadedTimetables, activeId) = CourseRepository.loadAllTimetables()
        if (loadedTimetables.isNotEmpty()) {
            AppState.timetables.clear()
            AppState.timetables.addAll(loadedTimetables)
            val resolvedId = activeId ?: loadedTimetables.first().id
            AppState.activeTimetableId.value = resolvedId
            val activeCourses = loadedTimetables.find { it.id == resolvedId }?.courses ?: emptyList()
            AppState.scheduledCourses.clear()
            AppState.scheduledCourses.addAll(activeCourses)
        } else {
            // First ever login — create a default timetable
            val defaultId = java.util.UUID.randomUUID().toString()
            val saved = CourseRepository.loadUserSchedule()
            val defaultTimetable = Timetable(id = defaultId, name = "My Timetable", courses = saved)
            AppState.timetables.add(defaultTimetable)
            AppState.activeTimetableId.value = defaultId
            AppState.scheduledCourses.clear()
            AppState.scheduledCourses.addAll(saved)
        }
    }

    // Save active timetable back to Firebase whenever courses change
    LaunchedEffect(scheduledCourses.toList(), activeTimetableId) {
        val id = activeTimetableId ?: return@LaunchedEffect
        val idx = AppState.timetables.indexOfFirst { it.id == id }
        if (idx >= 0) {
            AppState.timetables[idx] = AppState.timetables[idx].copy(courses = scheduledCourses.toList())
        }
        CourseRepository.saveAllTimetables(AppState.timetables.toList(), id)
    }

    // Export dialog (stable API — no experimental ModalBottomSheet)
    if (showExportSheet) {
        AlertDialog(
            onDismissRequest = { showExportSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Export Timetable", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    // Option 1: iCalendar
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportSheet = false
                                coroutineScope.launch {
                                    val ics = buildIcsContent(scheduledCourses)
                                    val ok = withContext(Dispatchers.IO) { saveIcsToDownloads(context, ics) }
                                    Toast.makeText(
                                        context,
                                        if (ok) "timetable.ics saved to Downloads" else "Export failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📅", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("iCalendar File (.ics)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Import into Google / Apple Calendar", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Image
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportSheet = false
                                coroutineScope.launch {
                                    val bmp = withContext(Dispatchers.Default) { renderTimetableBitmap(scheduledCourses) }
                                    val ok = withContext(Dispatchers.IO) { saveBitmapToDownloads(context, bmp) }
                                    Toast.makeText(
                                        context,
                                        if (ok) "timetable.png saved to Downloads" else "Export failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🖼️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Image (.png)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Save timetable as a PNG image", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportSheet = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = lightBackground,
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.SCHEDULE,
                onCoursesClick = onNavigateToCourses,
                onAiClick = onNavigateToAssistant,
                onScheduleClick = {},
                onChatbotClick = onNavigateToChatbot,
                onAdvisorClick = onNavigateToAdvisor,

                )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(lightBackground)
        ) {
            // Top user header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(primaryYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        if (nameInitial != null) {
                            Text(nameInitial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(displayName.ifBlank { "User" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("View Profile", fontSize = 13.sp, color = Color.Gray)
                    }
                }

                TextButton(onClick = onLogout) {
                    Text("Logout", color = Color.Gray, fontSize = 15.sp)
                }
            }

            // Timetable selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Dropdown with border + "+" with border
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .clickable { showTimetableDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timetables.find { it.id == activeTimetableId }?.name ?: "My Timetable",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select timetable",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTimetableDropdown,
                            onDismissRequest = { showTimetableDropdown = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            timetables.forEach { tt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            tt.name,
                                            fontWeight = if (tt.id == activeTimetableId) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tt.id == activeTimetableId) primaryYellow else Color.Black
                                        )
                                    },
                                    onClick = {
                                        showTimetableDropdown = false
                                        AppState.activeTimetableId.value = tt.id
                                        AppState.scheduledCourses.clear()
                                        AppState.scheduledCourses.addAll(tt.courses)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // "+" button with border
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable { showAddTimetableDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add timetable", tint = primaryYellow, modifier = Modifier.size(20.dp))
                    }
                }

                // Right: MoreVert with border (contains Clear, Delete, Export)
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color(0xFF555555), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear", color = Color.Gray) },
                            onClick = { showMoreMenu = false; showClearConfirmDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFE53935)) },
                            onClick = { showMoreMenu = false; showDeleteConfirmDialog = true }
                        )
                        HorizontalDivider(color = Color(0xFFEEEEEE))
                        DropdownMenuItem(
                            text = { Text("Export", color = Color.Black) },
                            onClick = { showMoreMenu = false; showExportSheet = true }
                        )
                    }
                }
            }

            // ── Clear confirmation dialog ──────────────────────────────────────
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = { Text("Clear Timetable?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "This will remove all courses from ${timetables.find { it.id == activeTimetableId }?.name ?: "this timetable"}. This cannot be undone.",
                            fontSize = 14.sp,
                            color = Color(0xFF555555)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showClearConfirmDialog = false
                                coroutineScope.launch {
                                    AppState.scheduledCourses.clear()
                                    val id = AppState.activeTimetableId.value ?: return@launch
                                    val idx = AppState.timetables.indexOfFirst { it.id == id }
                                    if (idx >= 0) AppState.timetables[idx] = AppState.timetables[idx].copy(courses = emptyList())
                                    CourseRepository.saveAllTimetables(AppState.timetables.toList(), id)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Yes, Clear", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            // ── Delete confirmation dialog ─────────────────────────────────────
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = { Text("Delete Timetable?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "${timetables.find { it.id == activeTimetableId }?.name ?: "This timetable"} and all its courses will be permanently deleted.",
                            fontSize = 14.sp,
                            color = Color(0xFF555555)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                coroutineScope.launch {
                                    val id = AppState.activeTimetableId.value ?: return@launch
                                    AppState.timetables.removeAll { it.id == id }
                                    // Switch to another timetable or create a blank default
                                    if (AppState.timetables.isNotEmpty()) {
                                        val next = AppState.timetables.first()
                                        AppState.activeTimetableId.value = next.id
                                        AppState.scheduledCourses.clear()
                                        AppState.scheduledCourses.addAll(next.courses)
                                        CourseRepository.saveAllTimetables(AppState.timetables.toList(), next.id)
                                    } else {
                                        // No timetables left — create a fresh default
                                        val newId = java.util.UUID.randomUUID().toString()
                                        val defaultTt = Timetable(id = newId, name = "My Timetable", courses = emptyList())
                                        AppState.timetables.add(defaultTt)
                                        AppState.activeTimetableId.value = newId
                                        AppState.scheduledCourses.clear()
                                        CourseRepository.saveAllTimetables(listOf(defaultTt), newId)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Yes, Delete", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            // Add Timetable dialog
            if (showAddTimetableDialog) {
                AlertDialog(
                    onDismissRequest = { showAddTimetableDialog = false; newTimetableName = "" },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = { Text("New Timetable", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = newTimetableName,
                            onValueChange = { newTimetableName = it },
                            placeholder = { Text("e.g. Winter 2026") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryYellow,
                                unfocusedBorderColor = Color(0xFFDDDDDD),
                                cursorColor = primaryYellow
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val name = newTimetableName.trim().ifBlank { "Timetable ${timetables.size + 1}" }
                                val newId = java.util.UUID.randomUUID().toString()
                                val newTt = Timetable(id = newId, name = name, courses = emptyList())
                                AppState.timetables.add(newTt)
                                AppState.activeTimetableId.value = newId
                                AppState.scheduledCourses.clear()
                                coroutineScope.launch {
                                    CourseRepository.saveAllTimetables(AppState.timetables.toList(), newId)
                                }
                                showAddTimetableDialog = false
                                newTimetableName = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Create", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTimetableDialog = false; newTimetableName = "" }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (scheduledCourses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Courses Added",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add courses to see your weekly schedule",
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    Button(
                        onClick = onNavigateToCourses,
                        modifier = Modifier.fillMaxWidth(0.8f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryYellow,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Add Courses", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    TimetableView(
                        courses = scheduledCourses,
                        courseColors = courseColors,
                        onRemoveCourse = { courseToRemove ->
                            coroutineScope.launch {
                                AppState.scheduledCourses.remove(courseToRemove)
                                CourseRepository.saveUserSchedule(AppState.scheduledCourses)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimetableView(
    courses: List<Course>,
    courseColors: Map<String, Color>,
    onRemoveCourse: (Course) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val startHour = 8
    val endHour = 23
    val hourHeight = 60.dp
    val primaryYellow = Color(0xFFFFD700)
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val totalWidth = maxWidth
        val timeColumnWidth = 48.dp
        val columnWidth = (totalWidth - timeColumnWidth) / days.size
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        Column(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(timeColumnWidth))
                days.forEach { day ->
                    Box(
                        modifier = Modifier.width(columnWidth).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(day, fontWeight = FontWeight.Bold, color = primaryYellow, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Time labels
                    Column(modifier = Modifier.width(timeColumnWidth), horizontalAlignment = Alignment.End) {
                        (startHour..endHour).forEach { hour ->
                            Box(modifier = Modifier.height(hourHeight).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(String.format(Locale.US, "%d:00", hour), fontSize = 12.sp, color = Color(0xFF888888))
                            }
                        }
                    }

                    // Grid
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(((endHour - startHour + 1) * hourHeight.value).dp)
                            .background(Color.White)
                            .drawBehind {
                                val strokeWidth = with(density) { 1.dp.toPx() }
                                val gray = Color(0xFFE0E0E0)
                                repeat(endHour - startHour + 1) { i ->
                                    val y = i * hourHeight.toPx()
                                    drawLine(gray, Offset(0f, y), Offset(size.width, y), strokeWidth)
                                }
                                repeat(days.size + 1) { i ->
                                    val x = i * (size.width / days.size)
                                    drawLine(gray, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                                }
                            }
                    ) {
                        courses.forEach { course ->
                            course.section.days.forEach { dayStr ->
                                val dayIdx = days.indexOf(dayStr)
                                if (dayIdx < 0) return@forEach
                                val startFloat = course.section.startTime.toFloat()
                                val endFloat = course.section.endTime.toFloat()
                                val durationHours = endFloat - startFloat
                                if (durationHours <= 0f) return@forEach
                                val topOffset = ((startFloat - startHour) * hourHeight.value).dp
                                val blockHeight = (durationHours * hourHeight.value).dp
                                val color = courseColors[course.code] ?: primaryYellow

                                Card(
                                    modifier = Modifier
                                        .offset(x = columnWidth * dayIdx + (columnWidth * 0.04f), y = topOffset)
                                        .width(columnWidth * 0.92f)
                                        .height(blockHeight)
                                        .padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = color),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    onClick = { selectedCourse = course }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(course.code, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, textAlign = TextAlign.Center)
                                        Text(course.section.component, fontSize = 11.sp, color = Color.Black.copy(alpha = 0.8f), maxLines = 1, textAlign = TextAlign.Center)
                                        Text("${course.section.startTime}-${course.section.endTime}", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Course detail dialog
        selectedCourse?.let { course ->
            AlertDialog(
                onDismissRequest = { selectedCourse = null },
                title = { Text("${course.code} – ${course.section.component}") },
                text = {
                    Column {
                        Text("Title: ${course.title}")
                        Text("Time: ${course.section.days.joinToString(", ")} ${course.section.startTime}–${course.section.endTime}")
                        Text("Location: ${course.section.location}")
                        Text("Units: ${course.units}")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onRemoveCourse(course); selectedCourse = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { selectedCourse = null }) { Text("Close") }
                }
            )
        }
    }
}
