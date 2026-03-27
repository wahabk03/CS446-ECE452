// HomeScreen.kt
package com.example.graphicaltimeplanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

/**
 * Term → (firstMonday, lastFriday) in yyyyMMdd format.
 * Covers UWaterloo's Winter 2026, Fall 2025, and Spring 2025 terms.
 */
private val termDateRanges = mapOf(
    "1261" to ("20260105" to "20260417"),  // Winter 2026
    "1259" to ("20250908" to "20251205"),  // Fall 2025
    "1255" to ("20250505" to "20250725")   // Spring 2025
)

/** Map from our day strings to the ISO weekday offsets from Monday (0=Mon…4=Fri). */
private val dayOffset = mapOf("Mon" to 0, "Tue" to 1, "Wed" to 2, "Thu" to 3, "Fri" to 4)
private val dayToIcal = mapOf(
    "Mon" to "MO", "Tue" to "TU", "Wed" to "WE",
    "Thu" to "TH", "Fri" to "FR", "Sat" to "SA", "Sun" to "SU"
)

/**
 * Return the yyyyMMdd string for the first occurrence of [day] on or after
 * the Monday given by [firstMondayStr] (format yyyyMMdd).
 */
private fun firstOccurrence(firstMondayStr: String, day: String): String {
    val offset = dayOffset[day] ?: 0
    val y = firstMondayStr.substring(0, 4).toInt()
    val m = firstMondayStr.substring(4, 6).toInt()
    val d = firstMondayStr.substring(6, 8).toInt()
    // Add offset days using simple carry arithmetic
    var day2 = d + offset
    var mon2 = m
    val daysInMonth = intArrayOf(0,31,28,31,30,31,30,31,31,30,31,30,31)
    if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) daysInMonth[2] = 29
    while (day2 > daysInMonth[mon2]) {
        day2 -= daysInMonth[mon2]; mon2++
    }
    return "%04d%02d%02d".format(y, mon2, day2)
}

/**
 * Fold long iCal lines per RFC 5545 §3.1 (max 75 octets, continuation
 * lines begin with a single space).
 */
private fun foldLine(line: String): String {
    if (line.length <= 75) return line
    val sb = StringBuilder()
    var i = 0
    var first = true
    while (i < line.length) {
        val take = if (first) 75 else 74
        val end = minOf(i + take, line.length)
        if (!first) sb.append("\r\n ")
        sb.append(line.substring(i, end))
        i = end; first = false
    }
    return sb.toString()
}

private fun buildIcsContent(courses: List<Course>, term: String = "1261"): String {
    val (firstMonday, lastFriday) = termDateRanges[term] ?: termDateRanges["1261"]!!
    val crlf = "\r\n"
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR$crlf")
    sb.append("VERSION:2.0$crlf")
    sb.append("PRODID:-//GraphicalTimePlanner//UWaterloo//EN$crlf")
    sb.append("CALSCALE:GREGORIAN$crlf")
    sb.append("METHOD:PUBLISH$crlf")

    for (course in courses) {
        val s = course.section
        if (s.days.isEmpty()) continue          // skip TBA sections
        if (s.startTime == s.endTime) continue  // skip zero-length sections

        // Use the first meeting day as the DTSTART anchor
        val anchorDay = s.days.first()
        val dateStr = firstOccurrence(firstMonday, anchorDay)
        val icalDays = s.days.mapNotNull { dayToIcal[it] }.joinToString(",")
        val uid = UUID.randomUUID().toString()
        val startStr = "%sT%02d%02d00".format(dateStr, s.startTime.hour, s.startTime.minute)
        val endStr   = "%sT%02d%02d00".format(dateStr, s.endTime.hour, s.endTime.minute)
        val untilStr = "${lastFriday}T235959Z"
        val location = s.location.ifBlank { "TBA" }

        sb.append("BEGIN:VEVENT$crlf")
        sb.append(foldLine("UID:$uid") + crlf)
        sb.append(foldLine("DTSTART:$startStr") + crlf)
        sb.append(foldLine("DTEND:$endStr") + crlf)
        sb.append(foldLine("RRULE:FREQ=WEEKLY;BYDAY=$icalDays;UNTIL=$untilStr") + crlf)
        sb.append(foldLine("SUMMARY:${course.code} - ${s.component}") + crlf)
        sb.append(foldLine("LOCATION:$location") + crlf)
        sb.append(foldLine("DESCRIPTION:${course.title}") + crlf)
        sb.append("END:VEVENT$crlf")
    }

    sb.append("END:VCALENDAR$crlf")
    return sb.toString()
}

private fun saveIcsToCache(context: Context, icsContent: String): android.net.Uri? {
    return try {
        // Write to app cache dir and expose via FileProvider so any calendar
        // app can read it — MediaStore Download URIs are not readable by third-
        // party apps without a FileProvider on API 29+.
        val file = java.io.File(context.cacheDir, "timetable.ics")
        file.writeText(icsContent, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    } catch (e: Exception) {
        null
    }
}

// ─── Timetable-to-Bitmap helper ───────────────────────────────────────────────

private val courseColorInts = listOf(
    Color(0xFFFFD700).toArgb(), Color(0xFFE1BEE7).toArgb(), Color(0xFFBBDEFB).toArgb(),
    Color(0xFFC8E6C9).toArgb(), Color(0xFFFFF9C4).toArgb()
)

private fun renderTimetableBitmap(courses: List<Course>): Bitmap {
    // Always render all 5 weekdays so the exported PNG matches the on-screen timetable
    val activeDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri")

    // Trim hours to actual schedule range (±30 min padding)
    val scheduleStart = courses.minOfOrNull { it.section.startTime.toFloat() }?.let {
        maxOf(8f, it - 0.5f) }.run { this?.toInt() ?: 8 }
    val scheduleEnd = courses.maxOfOrNull { it.section.endTime.toFloat() }?.let {
        minOf(22f, it + 0.5f) }.run { this?.toInt()?.plus(1) ?: 22 }

    val hourPx  = 100f          // taller rows → more readable text
    val timeColW = 90f
    val headerH  = 60f
    val colW     = 180f
    val padding  = 20f          // outer padding on all sides
    val totalW   = (padding * 2 + timeColW + colW * activeDays.size).toInt()
    val totalH   = (padding * 2 + headerH + (scheduleEnd - scheduleStart + 1) * hourPx).toInt()

    val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Background
    canvas.drawColor(android.graphics.Color.parseColor("#FAFAFA"))

    // Draw a white card area
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawRoundRect(
        RectF(padding, padding, totalW - padding, totalH - padding),
        16f, 16f, cardPaint
    )

    val gridPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#E8E8E8"); strokeWidth = 1.5f
    }
    val hourLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#AAAAAA"); textSize = 26f
    }
    val dayHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#333333")
        textSize = 28f; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val blockTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val blockSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#444444"); textSize = 21f
        textAlign = Paint.Align.CENTER
    }
    val blockTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#666666"); textSize = 19f
        textAlign = Paint.Align.CENTER
    }

    val gridLeft  = padding + timeColW
    val gridTop   = padding + headerH
    val gridRight = totalW - padding
    val gridBottom = totalH - padding

    // Day column dividers + headers
    activeDays.forEachIndexed { i, day ->
        val x = gridLeft + i * colW
        canvas.drawLine(x, padding, x, gridBottom, gridPaint)
        canvas.drawText(day, x + colW / 2f, padding + headerH - 16f, dayHeaderPaint)
    }
    canvas.drawLine(gridRight, padding, gridRight, gridBottom, gridPaint)

    // Header / grid separator
    canvas.drawLine(padding, gridTop, gridRight, gridTop, gridPaint)
    // Left time-column separator
    canvas.drawLine(gridLeft, padding, gridLeft, gridBottom, gridPaint)

    // Hour rows + labels
    for (h in scheduleStart..scheduleEnd) {
        val y = gridTop + (h - scheduleStart) * hourPx
        canvas.drawLine(gridLeft, y, gridRight, y, gridPaint)
        hourLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("%d:00".format(h), gridLeft - 8f, y + 20f, hourLabelPaint)
    }

    // Course color map
    val colorMap = mutableMapOf<String, Int>()
    courses.distinctBy { it.code }.forEachIndexed { i, c ->
        colorMap[c.code] = courseColorInts[i % courseColorInts.size]
    }

    val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val blockBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
        color = android.graphics.Color.parseColor("#22000000")
    }
    for (course in courses) {
        val argb = colorMap[course.code] ?: courseColorInts[0]
        blockPaint.color = argb
        for (day in course.section.days) {
            val di = activeDays.indexOf(day)
            if (di < 0) continue
            val startF = course.section.startTime.toFloat()
            val endF   = course.section.endTime.toFloat()
            if (endF <= startF) continue
            val inset = 5f
            val left   = gridLeft + di * colW + inset
            val top    = gridTop + (startF - scheduleStart) * hourPx + inset
            val right  = left + colW - inset * 2
            val bottom = gridTop + (endF - scheduleStart) * hourPx - inset
            val rect   = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 14f, 14f, blockPaint)
            canvas.drawRoundRect(rect, 14f, 14f, blockBorderPaint)

            val cx = (left + right) / 2f
            val blockH = bottom - top
            // Vertically centre text inside the block
            val lineH = 32f
            val totalTextH = when {
                blockH >= 100f -> lineH * 3
                blockH >= 60f  -> lineH * 2
                else           -> lineH
            }
            var textY = top + (blockH - totalTextH) / 2f + lineH

            canvas.drawText(course.code, cx, textY, blockTitlePaint)
            if (blockH >= 60f) {
                textY += lineH
                canvas.drawText(course.section.component, cx, textY, blockSubPaint)
            }
            if (blockH >= 100f) {
                textY += lineH - 4f
                canvas.drawText(
                    "${course.section.startTime}-${course.section.endTime}",
                    cx, textY, blockTimePaint
                )
            }
        }
    }
    return bmp
}

private fun saveBitmapToCache(context: Context, bmp: Bitmap): android.net.Uri? {
    return try {
        val file = java.io.File(context.cacheDir, "timetable.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    } catch (e: Exception) {
        null
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

    // Observe the SnapshotStateList directly so exports always get the live list.
    val scheduledCourses = AppState.scheduledCourses
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
    var newTimetableTerm by remember { mutableStateOf("1261") }  // default: Winter 2026
    // True while the initial Firebase load is in flight — prevents
    // the "no timetable" prompt from flashing before data arrives.
    var isFirstLoad by remember { mutableStateOf(true) }

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
        // Only keep timetables that have a term set. Termless ones are
        // legacy defaults ("My Timetable") — treat them as non-existent
        // so the user is prompted to create a proper term-scoped timetable.
        val validTimetables = loadedTimetables.filter { it.term.isNotBlank() }
        if (validTimetables.isNotEmpty()) {
            AppState.timetables.clear()
            AppState.timetables.addAll(validTimetables)
            val resolvedId = (if (activeId != null && validTimetables.any { it.id == activeId }) activeId
            else validTimetables.first().id)
            AppState.activeTimetableId.value = resolvedId
            val activeCourses = validTimetables.find { it.id == resolvedId }?.courses ?: emptyList()
            AppState.scheduledCourses.clear()
            AppState.scheduledCourses.addAll(activeCourses)
        }
        // No valid timetables — leave AppState empty, UI will prompt the user.
        isFirstLoad = false
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
                                    val term = AppState.timetables
                                        .find { it.id == activeTimetableId }?.term ?: "1261"
                                    val ics = buildIcsContent(scheduledCourses, term)
                                    val uri = withContext(Dispatchers.IO) {
                                        saveIcsToCache(context, ics)
                                    }
                                    if (uri != null) {
                                        // ACTION_SEND lets the user pick any calendar app.
                                        // Using FLAG_GRANT_READ_URI_PERMISSION so the receiving
                                        // app can read our FileProvider URI.
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/calendar"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "Import into calendar")
                                        )
                                    } else {
                                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                    }
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
                                    val bmp = withContext(Dispatchers.Default) {
                                        renderTimetableBitmap(scheduledCourses)
                                    }
                                    val uri = withContext(Dispatchers.IO) {
                                        saveBitmapToCache(context, bmp)
                                    }
                                    if (uri != null) {
                                        // Offer share sheet so user can save/send immediately
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(shareIntent, "Share timetable")
                                        )
                                    } else {
                                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                    }
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

            // Timetable selector row — hidden until user has at least one timetable
            if (timetables.isNotEmpty()) Row(
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
                            Column {
                                Text(
                                    text = timetables.find { it.id == activeTimetableId }?.name ?: "My Timetable",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val activeTerm = timetables.find { it.id == activeTimetableId }?.term ?: ""
                                val activeTermLabel = CourseRepository.TERM_MAPPINGS
                                    .find { it.first == activeTerm }?.second ?: ""
                                if (activeTermLabel.isNotEmpty()) {
                                    Text(
                                        text = activeTermLabel,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
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
                                        Column {
                                            Text(
                                                tt.name,
                                                fontWeight = if (tt.id == activeTimetableId) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tt.id == activeTimetableId) primaryYellow else Color.Black,
                                                fontSize = 15.sp
                                            )
                                            val ttTermLabel = CourseRepository.TERM_MAPPINGS
                                                .find { it.first == tt.term }?.second ?: ""
                                            if (ttTermLabel.isNotEmpty()) {
                                                Text(
                                                    text = ttTermLabel,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
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
                    onDismissRequest = {
                        showAddTimetableDialog = false
                        newTimetableName = ""
                        newTimetableTerm = "1261"
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color.White,
                    title = { Text("New Timetable", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            // ── Name field ────────────────────────────────────
                            Text("Plan Name", fontSize = 13.sp, color = Color.Gray,
                                fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newTimetableName,
                                onValueChange = { newTimetableName = it },
                                placeholder = { Text("e.g. My Fall Plan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryYellow,
                                    unfocusedBorderColor = Color(0xFFDDDDDD),
                                    cursorColor = primaryYellow
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Term picker ───────────────────────────────────
                            Text("Term", fontSize = 13.sp, color = Color.Gray,
                                fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CourseRepository.TERM_MAPPINGS.forEach { (termCode, termLabel) ->
                                    val isSelected = newTimetableTerm == termCode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { newTimetableTerm = termCode },
                                        label = {
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Text(
                                                    text = termLabel,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = primaryYellow,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFFF5F5F5),
                                            labelColor = Color.DarkGray
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            selectedBorderColor = primaryYellow,
                                            borderColor = Color(0xFFDDDDDD)
                                        )
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val termLabel = CourseRepository.TERM_MAPPINGS
                                    .find { it.first == newTimetableTerm }?.second ?: ""
                                // Auto-fill name from term label if left blank
                                val name = newTimetableName.trim().ifBlank { termLabel.ifBlank { "Timetable ${timetables.size + 1}" } }
                                val newId = java.util.UUID.randomUUID().toString()
                                val newTt = Timetable(
                                    id = newId,
                                    name = name,
                                    courses = emptyList(),
                                    term = newTimetableTerm
                                )
                                AppState.timetables.add(newTt)
                                AppState.activeTimetableId.value = newId
                                AppState.scheduledCourses.clear()
                                coroutineScope.launch {
                                    CourseRepository.saveAllTimetables(AppState.timetables.toList(), newId)
                                }
                                showAddTimetableDialog = false
                                newTimetableName = ""
                                newTimetableTerm = "1261"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Create", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddTimetableDialog = false
                            newTimetableName = ""
                            newTimetableTerm = "1261"
                        }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                // Still waiting for Firebase — show nothing to avoid flicker
                isFirstLoad -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryYellow)
                    }
                }

                // New user (or all timetables deleted) — force them to create one
                timetables.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Timetable Yet",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap '+ Create Timetable' below to create your first timetable and choose a term.",
                            fontSize = 15.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { showAddTimetableDialog = true },
                            modifier = Modifier.fillMaxWidth(0.7f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Timetable", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Active timetable exists but has no term — prompt to create a proper one
                timetables.find { it.id == activeTimetableId }?.term.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Timetable Yet",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap '+ Create Timetable' below to create your first timetable and choose a term.",
                            fontSize = 15.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { showAddTimetableDialog = true },
                            modifier = Modifier.fillMaxWidth(0.7f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Timetable", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Timetable exists but has no courses yet
                scheduledCourses.isEmpty() -> {
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
                }

                // Has courses — show the timetable grid
                else ->
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

/** Represents one overlapping time window on a specific day column. */
private data class ConflictSpan(
    val day: String,
    val overlapStart: Float,   // fractional hours, e.g. 10.5 = 10:30
    val overlapEnd: Float
)

/**
 * Returns one ConflictSpan per overlapping pair per shared day.
 * The box covers the full union: min(a.start, b.start) → max(a.end, b.end),
 * i.e. from the earlier start to the later end of the two courses.
 */
private fun findConflictSpans(courses: List<Course>): List<ConflictSpan> {
    val spans = mutableListOf<ConflictSpan>()
    for (i in courses.indices) {
        for (j in i + 1 until courses.size) {
            val a = courses[i]
            val b = courses[j]
            val sharedDays = a.section.days.intersect(b.section.days.toSet())
            if (sharedDays.isEmpty()) continue
            val aStart = a.section.startTime.toFloat()
            val aEnd   = a.section.endTime.toFloat()
            val bStart = b.section.startTime.toFloat()
            val bEnd   = b.section.endTime.toFloat()
            // Only create a span if they actually overlap in time
            if (aStart < bEnd && bStart < aEnd) {
                val unionStart = minOf(aStart, bStart)
                val unionEnd   = maxOf(aEnd,   bEnd)
                sharedDays.forEach { day -> spans += ConflictSpan(day, unionStart, unionEnd) }
            }
        }
    }
    return spans
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

    // Compute exact overlapping time windows for each conflicting pair
    val conflictSpans = remember(courses) { findConflictSpans(courses) }

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

                                val courseIsConflicting = conflictSpans.any { span ->
                                    span.day == dayStr &&
                                            course.section.startTime.toFloat() < span.overlapEnd &&
                                            course.section.endTime.toFloat() > span.overlapStart
                                }
                                Card(
                                    modifier = Modifier
                                        .offset(x = columnWidth * dayIdx + (columnWidth * 0.04f), y = topOffset)
                                        .width(columnWidth * 0.92f)
                                        .height(blockHeight)
                                        .padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = color),
                                    elevation = CardDefaults.cardElevation(if (courseIsConflicting) 6.dp else 2.dp),
                                    border = if (courseIsConflicting)
                                        androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD32F2F))
                                    else null,
                                    onClick = { selectedCourse = course }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Top,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Red stripe at the top of conflicting cards
                                        if (courseIsConflicting) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(
                                                        Color(0xFFD32F2F),
                                                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                                                    )
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp),
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

                        // ── Conflict overlay boxes ────────────────────────────
                        // One red box per conflict span, covering exactly the
                        // overlapping time window (max of starts → min of ends).
                        conflictSpans.forEach { span ->
                            val dayIdx = days.indexOf(span.day)
                            if (dayIdx < 0) return@forEach
                            val topOffset = ((span.overlapStart - startHour) * hourHeight.value).dp
                            val boxHeight = ((span.overlapEnd - span.overlapStart) * hourHeight.value).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = columnWidth * dayIdx, y = topOffset)
                                    .width(columnWidth)
                                    .height(boxHeight)
                                    // Solid vivid red background so it punches through course cards
                                    .background(Color(0xCCD32F2F), RoundedCornerShape(6.dp))
                                    .border(2.5.dp, Color(0xFFB71C1C), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "⚠️",
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "CONFLICT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Course detail dialog
        selectedCourse?.let { course ->
            val isConflict = conflictSpans.any { span ->
                course.section.days.contains(span.day) &&
                        course.section.startTime.toFloat() < span.overlapEnd &&
                        course.section.endTime.toFloat() > span.overlapStart  // overlaps with the union window
            }
            AlertDialog(
                onDismissRequest = { selectedCourse = null },
                title = { Text("${course.code} – ${course.section.component}") },
                text = {
                    Column {
                        Text("Title: ${course.title}")
                        Text("Time: ${course.section.days.joinToString(", ")} ${course.section.startTime}–${course.section.endTime}")
                        Text("Location: ${course.section.location}")
                        Text("Units: ${course.units}")
                        if (isConflict) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFD32F2F), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚠️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Time Conflict Detected",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "This course overlaps with another in your schedule.",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
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