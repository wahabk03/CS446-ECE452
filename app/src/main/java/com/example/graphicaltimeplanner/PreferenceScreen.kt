// PreferenceScreen.kt
package com.example.graphicaltimeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Locale

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
    // ── Scheduling preferences — backed by AppState so they survive navigation ─
    var avoidEarlyClasses by AppState.avoidEarlyClasses
    var earlyClassCutoff   by AppState.earlyClassCutoff
    var minimizeGaps       by AppState.minimizeGaps
    val clusterDays        = AppState.clusterDays
    var maxDailyHours      by AppState.maxDailyHours

    // ── Course selection ──────────────────────────────────────────────────────
    // ── Active term — mirrors CourseScreen so both screens browse the same courses ──
    val activeTimetableId by AppState.activeTimetableId
    val selectedTerm by remember {
        androidx.compose.runtime.derivedStateOf {
            AppState.timetables.find { it.id == activeTimetableId }?.term
                ?.takeIf { it.isNotBlank() } ?: "1261"
        }
    }
    val selectedTermLabel by remember {
        androidx.compose.runtime.derivedStateOf {
            CourseRepository.TERM_MAPPINGS
                .find { it.first == selectedTerm }?.second ?: ""
        }
    }

    var selectedSubject by remember { mutableStateOf("CS") }
    var searchQuery     by remember { mutableStateOf("") }
    var courses         by remember { mutableStateOf(listOf<Course>()) }
    var isLoading       by remember { mutableStateOf(true) }
    // Backed by AppState so selection survives navigation; cleared on logout
    val selectedCourseCodes = AppState.selectedCourseCodes
    // Pinned sections: key = "$code||$componentType" → specific Section chosen by user
    val selectedSections    = AppState.selectedSections

    // Course data cache — lives in AppState so it survives navigation.
    // Key = "$term||$subject" so switching timetable term invalidates stale entries.
    val courseCache = AppState.courseCache

    // ── Generate state ────────────────────────────────────────────────────────
    var isGenerating           by remember { mutableStateOf(false) }
    var showNoResultsDialog    by remember { mutableStateOf(false) }
    var showAppliedDialog      by remember { mutableStateOf(false) }
    var generatedSchedules     by remember { mutableStateOf<List<List<Course>>>(emptyList()) }
    var currentScheduleIndex   by remember { mutableStateOf(0) }
    var showScheduleDialog     by remember { mutableStateOf(false) }
    // Courses that are impossible to satisfy under the current early-class cutoff.
    // Non-empty → the earlyConflictDialog is shown instead of the schedule.
    var earlyConflictCourses    by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEarlyConflictDialog by remember { mutableStateOf(false) }
    // Courses that are impossible to satisfy under the current max-daily-hours cap.
    // Non-empty → the dailyHoursConflictDialog is shown instead of the schedule.
    var dailyHoursConflictCourses    by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDailyHoursConflictDialog by remember { mutableStateOf(false) }

    // Resolve which subject to fetch:
    //  - If the search box is blank → use the selected tab (selectedSubject)
    //  - If the search box has text → try to parse a subject prefix from it
    //    (e.g. "CS 446" → "CS", "cs446" → "CS").  If one is found, fetch
    //    that subject regardless of which tab is active.  If no prefix matches,
    //    fall back to the selected tab so the list is never empty.
    val subjectFromQuery = remember(searchQuery) {
        val q = searchQuery.trim().replace(" ", "").uppercase()
        if (q.isEmpty()) null
        else CourseRepository.ALL_SUBJECTS
            .sortedByDescending { it.length }          // longest match wins
            .firstOrNull { subj -> q.startsWith(subj) }
    }

    // The subject we actually fetch from Firestore.
    val fetchSubject = if (!subjectFromQuery.isNullOrEmpty()) subjectFromQuery else selectedSubject

    LaunchedEffect(fetchSubject, selectedTerm) {
        isLoading = true
        try {
            val cacheKey = "$selectedTerm||$fetchSubject"
            val cached = courseCache[cacheKey]
            if (cached != null) {
                courses = cached
            } else {
                val fetched = CourseRepository.getCourses(term = selectedTerm, subject = fetchSubject)
                courses = fetched
                courseCache[cacheKey] = fetched
            }
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
            // Normalise: collapse spaces so "phil101" and "phil 101" both work.
            val qNorm = searchQuery.trim().lowercase().replace(" ", "")
            groupedCourses.filter { group ->
                val codeNorm  = group.code.lowercase().replace(" ", "")
                val titleLower = group.title.lowercase()
                codeNorm.contains(qNorm) ||
                        titleLower.contains(searchQuery.trim().lowercase())
            }
        }
    }

    // Subject chips
    val subjects = CourseRepository.ALL_SUBJECTS

    // ── Generate optimal timetable logic (Genetic Algorithm) ─────────────────
    //
    // DESIGN GOALS
    //  1. Every selected course must appear in the result — including ALL of its
    //     component types (LEC + LAB + TUT etc.).  A schedule that is missing any
    //     component of any selected course is NEVER returned to the user.
    //  2. Scheduling preferences (avoid-early, minimize-gaps, cluster-days,
    //     max-daily-hours) are respected as soft constraints via the fitness score.
    //  3. Hard conflict constraint: no two sections may overlap in time on the
    //     same day.
    //
    // ALGORITHM OVERVIEW — Genetic Algorithm with repair operator
    //  A population of chromosomes (IntArray) evolves over 200 generations.
    //  Each gene encodes which candidate section to use for one (course × component)
    //  slot.  A repair operator fixes hard-constraint violations at decode time so
    //  that evolution pressure naturally moves the population toward fully-feasible
    //  schedules.  The top 5 distinct feasible individuals from the final population
    //  are returned to the user, ranked by the soft-preference score.
    //
    // FITNESS
    //  • +10 000 per successfully placed gene (after repair)
    //  • +50 000 bonus when ALL genes are placed (fully feasible)
    //  • +scheduleScore() soft preference reward (gap, early, cluster)
    //
    fun generateTimetable() {
        if (selectedCourseCodes.isEmpty()) return
        isGenerating = true

        val allCachedCourses = courseCache
            .filter { it.key.startsWith("$selectedTerm||") }
            .values.flatten()

        coroutineScope.launch {
            try {
                // Snapshot ALL preferences before entering the coroutine body.
                // These are Compose-state delegates; reading them off the main thread
                // is unsafe, and snapshotting them ensures consistency across the
                // entire GA run even if the user changes a toggle mid-generation.
                val avoidEarly    = avoidEarlyClasses
                val cutoff        = earlyClassCutoff
                val minGaps       = minimizeGaps
                val maxHours      = maxDailyHours
                val preferredDays = clusterDays.filter { it.value }.keys.toSet()

                // ── Build one slot per (courseCode × componentType) ──────────────
                // Each slot holds all candidate sections sorted best-first so DFS
                // naturally finds preference-satisfying solutions with fewer backtracks.
                data class Slot(
                    val courseCode: String,
                    val componentType: String,
                    val candidates: List<Course>
                )

                // Lower prefScore = more preferred (DFS tries ascending order)
                fun prefScore(c: Course): Int {
                    var s = 0
                    // Penalise sections whose start time is before the cutoff hour
                    if (avoidEarly && c.section.startTime.hour < cutoff) {
                        // Stronger penalty the earlier it is
                        s += (cutoff - c.section.startTime.hour) * 40
                    }
                    // Penalise sections that fall on NO preferred day when cluster is active
                    if (preferredDays.isNotEmpty() && c.section.days.none { it in preferredDays }) {
                        s += 80
                    }
                    return s
                }

                // Snapshot pinned sections so the coroutine captures the right values
                val pinnedSections = selectedSections.toMap()

                val slots: List<Slot> = allCachedCourses
                    .filter { selectedCourseCodes.contains(it.code) }
                    // Exclude TBA / online sections that have no parseable schedule —
                    // they have empty days lists and can't be placed on the grid.
                    .filter { it.section.days.isNotEmpty() }
                    .groupBy { "${it.code}||${it.section.componentType}" }
                    .map { (key, secs) ->
                        val (code, compType) = key.split("||", limit = 2)
                        val pinKey = "$code||$compType"
                        val pinned = pinnedSections[pinKey]
                        // If the user pinned a specific section, use ONLY that section
                        // as the candidate (hard constraint). Otherwise use all sections
                        // sorted by soft-preference score.
                        val candidates = if (pinned != null) {
                            secs.filter { it.section.classNumber == pinned.classNumber }
                                .ifEmpty { secs.sortedBy { prefScore(it) } }
                        } else {
                            secs.sortedBy { prefScore(it) }
                        }
                        Slot(code, compType, candidates)
                    }
                    // Pinned slots have exactly 1 candidate — sort them first so the
                    // GA repair operator commits to them immediately (reduces search space).
                    .sortedWith(compareBy({ it.candidates.size }, { it.courseCode }, { it.componentType }))

                if (slots.isEmpty()) {
                    isGenerating = false
                    showNoResultsDialog = true
                    return@launch
                }

                // ── Early-class hard-constraint pre-check ────────────────────────
                // If avoidEarlyClasses is on, verify that every slot has at least
                // one candidate that starts at or after the cutoff hour.
                // A slot with ONLY early candidates means the preference is impossible
                // to satisfy for that course component — we must tell the user rather
                // than silently showing a violating schedule.
                if (avoidEarly) {
                    val impossibleCourses = slots
                        .filter { slot ->
                            slot.candidates.all { it.section.startTime.hour < cutoff }
                        }
                        .map { it.courseCode }
                        .distinct()

                    if (impossibleCourses.isNotEmpty()) {
                        isGenerating = false
                        earlyConflictCourses = impossibleCourses
                        showEarlyConflictDialog = true
                        return@launch
                    }
                }

                // ── Max-daily-hours hard-constraint pre-check ────────────────────
                // A single section whose own duration already exceeds maxHours can
                // NEVER be placed on any day without breaching the cap — regardless
                // of what other courses are chosen.  Catch this before the GA runs.
                val dailyHoursImpossible = slots
                    .filter { slot ->
                        // All candidates for this slot are individually too long
                        slot.candidates.all { c ->
                            val dur = c.section.endTime.toFloat() - c.section.startTime.toFloat()
                            dur > maxHours
                        }
                    }
                    .map { it.courseCode }
                    .distinct()

                if (dailyHoursImpossible.isNotEmpty()) {
                    isGenerating = false
                    dailyHoursConflictCourses = dailyHoursImpossible
                    showDailyHoursConflictDialog = true
                    return@launch
                }

                // ── Helpers ──────────────────────────────────────────────────────

                // Duration in fractional hours (same unit as Time.toFloat())
                fun sectionDuration(c: Course) =
                    c.section.endTime.toFloat() - c.section.startTime.toFloat()

                // True if candidate overlaps any already-placed section on a shared day
                fun conflictsWith(candidate: Course, placed: List<Course>): Boolean {
                    val cDays = candidate.section.days.toSet()
                    val cStart = candidate.section.startTime
                    val cEnd   = candidate.section.endTime
                    return placed.any { p ->
                        p.section.days.any { it in cDays } &&
                                cStart < p.section.endTime &&
                                p.section.startTime < cEnd
                    }
                }

                // ── Soft-preference score for a complete schedule ────────────────
                // Higher is better. Used to rank the collected solutions.
                fun scheduleScore(placed: List<Course>): Int {
                    var score = 0

                    // ① Minimize gaps: subtract total idle minutes between consecutive
                    //    classes on the same day (only positive gaps — no overlap penalty
                    //    here since overlap is already a hard constraint).
                    if (minGaps && placed.size > 1) {
                        val byDay = placed
                            .flatMap { c -> c.section.days.map { d -> d to c } }
                            .groupBy({ it.first }, { it.second })
                        val gapMinutes = byDay.values.sumOf { daySecs ->
                            if (daySecs.size < 2) 0
                            else {
                                val sorted = daySecs.sortedBy { it.section.startTime }
                                sorted.zipWithNext().sumOf { (a, b) ->
                                    val gap = ((b.section.startTime.toFloat() - a.section.endTime.toFloat()) * 60)
                                        .toInt().coerceAtLeast(0)
                                    gap
                                }
                            }
                        }
                        score -= gapMinutes
                    }

                    // ② Avoid early classes: penalise every section that starts before
                    //    the user's chosen cutoff hour, proportional to how early it is.
                    if (avoidEarly) {
                        placed.forEach { c ->
                            val startHour = c.section.startTime.hour
                            if (startHour < cutoff) {
                                score -= (cutoff - startHour) * 60   // minutes earlier × 60
                            }
                        }
                    }

                    // ③ Cluster days: penalise each section that falls on a day
                    //    NOT in the preferred set. Weight per section so multiple
                    //    off-day meetings hurt more.
                    if (preferredDays.isNotEmpty()) {
                        placed.forEach { c ->
                            val offDayCount = c.section.days.count { it !in preferredDays }
                            score -= offDayCount * 120
                        }
                    }

                    return score
                }

                // ── Genetic Algorithm ─────────────────────────────────────────────
                //
                // CHROMOSOME ENCODING
                //  A chromosome is an IntArray of length = slots.size.
                //  chromosome[i] is an index into slots[i].candidates.
                //  Decoding a chromosome produces one candidate section per slot
                //  (before the repair operator is applied).
                //
                // REPAIR OPERATOR
                //  After plain decoding we walk the genes in order. If a chosen
                //  section conflicts with an already-placed section (time overlap) or
                //  would push any day over maxDailyHours, we scan the other candidates
                //  in that slot for a valid replacement. If none exists the gene is
                //  left as-is but the section is not placed — the fitness function
                //  penalises missing genes heavily so evolution pressures the
                //  population toward feasible chromosomes.
                //
                // FITNESS  (higher is better)
                //  • +10 000 per gene successfully placed after repair
                //  • +50 000 bonus when every gene is placed (fully feasible schedule)
                //  • +scheduleScore(placed) — the soft preference score from above
                //    (gap penalty, early-class penalty, cluster-day penalty)
                //
                // GA PARAMETERS
                //  POPULATION_SIZE = 120   — large enough for diversity
                //  GENERATIONS     = 200   — enough for convergence on typical inputs
                //  ELITE_COUNT     = 6     — top individuals survive unchanged
                //  TOURNAMENT_K    = 5     — tournament selection pressure
                //  CROSSOVER_RATE  = 0.85  — high crossover for exploration
                //  MUTATION_RATE   = 0.08  — per-gene flip probability
                //  MAX_RESULTS     = 5     — distinct feasible schedules returned

                val POPULATION_SIZE = 120
                val GENERATIONS     = 200
                val ELITE_COUNT     = 6
                val TOURNAMENT_K    = 5
                val CROSSOVER_RATE  = 0.85
                val MUTATION_RATE   = 0.08
                val MAX_RESULTS     = 5

                // Use nanoTime so every invocation — even with identical course
                // selections and preferences — produces a different random stream,
                // which is what makes the GA non-deterministic across runs.
                val rng = java.util.Random(System.nanoTime())

                // ── Decode + repair a chromosome into a list of placed sections ──
                // IMPORTANT: works on an internal copy so the caller's chromosome
                // is never mutated.  Mutating in-place during fitness evaluation
                // destroys population diversity and makes the GA deterministic.
                fun decode(chromosome: IntArray): List<Course> {
                    val genes      = chromosome.copyOf()   // ← work on a copy; never mutate caller's array
                    val placed     = mutableListOf<Course>()
                    val dailyHours = mutableMapOf<String, Float>()

                    for (i in slots.indices) {
                        val slot       = slots[i]
                        val startGene  = genes[i].coerceIn(0, slot.candidates.lastIndex)
                        var placed_sec: Course? = null

                        // Try startGene first, then scan remaining candidates for a valid one
                        val tryOrder = (startGene until slot.candidates.size) +
                                (0 until startGene)

                        for (ci in tryOrder) {
                            val candidate = slot.candidates[ci]

                            // Hard constraint 1 — no time overlap
                            if (conflictsWith(candidate, placed)) continue

                            // Hard constraint 2 — daily hour cap
                            val dur = sectionDuration(candidate)
                            val exceedsDaily = candidate.section.days.any { day ->
                                (dailyHours[day] ?: 0f) + dur > maxHours
                            }
                            if (exceedsDaily) continue

                            // Valid placement found — record repaired index in local copy only
                            placed_sec = candidate
                            genes[i] = ci
                            break
                        }

                        if (placed_sec != null) {
                            placed.add(placed_sec)
                            placed_sec.section.days.forEach { day ->
                                dailyHours[day] = (dailyHours[day] ?: 0f) + sectionDuration(placed_sec)
                            }
                        }
                    }
                    return placed
                }

                // ── Fitness function ──────────────────────────────────────────────
                fun fitness(chromosome: IntArray): Int {
                    val placed = decode(chromosome)
                    val placedCount = placed.size
                    val fullyFeasible = placedCount == slots.size
                    return placedCount * 10_000 +
                            (if (fullyFeasible) 50_000 else 0) +
                            scheduleScore(placed)
                }

                // ── Random chromosome factory ─────────────────────────────────────
                fun randomChromosome(): IntArray = IntArray(slots.size) { i ->
                    if (slots[i].candidates.isEmpty()) 0
                    else rng.nextInt(slots[i].candidates.size)
                }

                // ── Tournament selection ──────────────────────────────────────────
                fun tournamentSelect(
                    population: List<IntArray>,
                    fitnesses: IntArray
                ): IntArray {
                    var best = rng.nextInt(population.size)
                    repeat(TOURNAMENT_K - 1) {
                        val challenger = rng.nextInt(population.size)
                        if (fitnesses[challenger] > fitnesses[best]) best = challenger
                    }
                    return population[best].copyOf()
                }

                // ── Uniform crossover ─────────────────────────────────────────────
                fun crossover(parentA: IntArray, parentB: IntArray): IntArray {
                    if (rng.nextDouble() > CROSSOVER_RATE) return parentA.copyOf()
                    return IntArray(slots.size) { i ->
                        if (rng.nextBoolean()) parentA[i] else parentB[i]
                    }
                }

                // ── Per-gene mutation (random reset) ──────────────────────────────
                fun mutate(chromosome: IntArray) {
                    for (i in slots.indices) {
                        if (rng.nextDouble() < MUTATION_RATE && slots[i].candidates.size > 1) {
                            chromosome[i] = rng.nextInt(slots[i].candidates.size)
                        }
                    }
                }

                // ── Initialise population — fully random so every run explores a
                //    different region of the search space (true GA non-determinism).
                //    The soft-preference sort on candidates and the repair operator
                //    steer evolution toward good schedules without biasing the seed.
                var population = MutableList(POPULATION_SIZE) { randomChromosome() }

                var fitnesses = IntArray(POPULATION_SIZE) { fitness(population[it]) }

                // ── Main GA loop ──────────────────────────────────────────────────
                repeat(GENERATIONS) {
                    // Elitism — carry top ELITE_COUNT individuals unchanged
                    val eliteIndices = fitnesses
                        .withIndex()
                        .sortedByDescending { it.value }
                        .take(ELITE_COUNT)
                        .map { it.index }

                    val nextPop = MutableList(POPULATION_SIZE) { randomChromosome() }
                    eliteIndices.forEachIndexed { pos, origIdx ->
                        nextPop[pos] = population[origIdx].copyOf()
                    }

                    // Fill the rest through selection + crossover + mutation
                    for (j in ELITE_COUNT until POPULATION_SIZE) {
                        val child = crossover(
                            tournamentSelect(population, fitnesses),
                            tournamentSelect(population, fitnesses)
                        )
                        mutate(child)
                        nextPop[j] = child
                    }

                    population = nextPop
                    fitnesses  = IntArray(POPULATION_SIZE) { fitness(population[it]) }
                }

                // ── Harvest distinct feasible schedules from the final population ─
                fun fingerprint(sched: List<Course>) = sched
                    .sortedWith(compareBy({ it.code }, { it.section.componentType }, { it.section.component }))
                    .joinToString("||") {
                        "${it.code}|${it.section.component}|${it.section.days.joinToString(",")}|${it.section.startTime}|${it.section.endTime}"
                    }

                val results = mutableListOf<List<Course>>()
                val seen    = mutableSetOf<String>()

                // Walk from best to worst fitness to collect top distinct schedules
                val rankedIndices = fitnesses
                    .withIndex()
                    .sortedByDescending { it.value }
                    .map { it.index }

                for (idx in rankedIndices) {
                    if (results.size >= MAX_RESULTS) break
                    val placed = decode(population[idx])
                    if (placed.size < slots.size) continue          // skip infeasible
                    // Post-GA early-class hard filter: discard any schedule that still
                    // contains a section starting before the cutoff.  This catches
                    // pinned sections that bypass the candidate list filter, and acts
                    // as a safety net even when the pre-check passed.
                    if (avoidEarly && placed.any { it.section.startTime.hour < cutoff }) continue
                    // Post-GA max-daily-hours hard filter: discard any schedule where
                    // the total class time on any single day exceeds maxHours.
                    val dailyTotals = mutableMapOf<String, Float>()
                    placed.forEach { c ->
                        val dur = c.section.endTime.toFloat() - c.section.startTime.toFloat()
                        c.section.days.forEach { day ->
                            dailyTotals[day] = (dailyTotals[day] ?: 0f) + dur
                        }
                    }
                    if (dailyTotals.values.any { it > maxHours }) continue
                    val fp = fingerprint(placed)
                    if (seen.add(fp)) results.add(placed)
                }

                val sorted = results.sortedByDescending { scheduleScore(it) }

                generatedSchedules   = sorted
                currentScheduleIndex = 0
                isGenerating         = false

                if (sorted.isEmpty()) {
                    // Surface the most specific error possible.
                    // Priority: early-class conflict → daily-hours conflict → generic.
                    val earlyViolators = if (avoidEarly) slots
                        .filter { slot -> slot.candidates.all { it.section.startTime.hour < cutoff } }
                        .map { it.courseCode }.distinct()
                    else emptyList()

                    val dailyViolators = slots
                        .filter { slot ->
                            slot.candidates.all { c ->
                                val dur = c.section.endTime.toFloat() - c.section.startTime.toFloat()
                                dur > maxHours
                            }
                        }
                        .map { it.courseCode }.distinct()

                    when {
                        earlyViolators.isNotEmpty() -> {
                            earlyConflictCourses = earlyViolators
                            showEarlyConflictDialog = true
                        }
                        dailyViolators.isNotEmpty() -> {
                            dailyHoursConflictCourses = dailyViolators
                            showDailyHoursConflictDialog = true
                        }
                        else -> showNoResultsDialog = true
                    }
                } else showScheduleDialog = true

            } catch (_: Exception) {
                isGenerating = false
                showNoResultsDialog = true
            }
        }
    }

    // ── Generated schedule dialog — full-screen graphical timetable preview ──
    if (showScheduleDialog && generatedSchedules.isNotEmpty()) {
        val schedule = generatedSchedules[currentScheduleIndex]

        val previewCourseColors = remember(schedule) {
            val palette = listOf(
                Color(0xFFFFD700), Color(0xFFE1BEE7), Color(0xFFBBDEFB),
                Color(0xFFC8E6C9), Color(0xFFFFF9C4)
            )
            val map = mutableMapOf<String, Color>()
            schedule.distinctBy { it.code }.forEachIndexed { i, c ->
                map[c.code] = palette[i % palette.size]
            }
            map
        }

        Dialog(
            onDismissRequest = { showScheduleDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Schedule ${currentScheduleIndex + 1} of ${generatedSchedules.size}",
                            fontWeight = FontWeight.Bold, fontSize = 17.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { currentScheduleIndex-- }, enabled = currentScheduleIndex > 0) { Text("← Prev") }
                            TextButton(onClick = { currentScheduleIndex++ }, enabled = currentScheduleIndex < generatedSchedules.size - 1) { Text("Next →") }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        TimetableView(
                            courses = schedule,
                            courseColors = previewCourseColors,
                            onRemoveCourse = { /* read-only preview */ },
                            readOnly = true
                        )
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showScheduleDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Close", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // Build a unique name and ID for the new timetable
                                    val newId   = java.util.UUID.randomUUID().toString()
                                    val newName = "Generated Timetable ${AppState.timetables.size + 1}"

                                    // Derive the term from the currently active timetable so the
                                    // new timetable is labelled correctly (fall back to "1261").
                                    val newTerm = AppState.timetables
                                        .find { it.id == AppState.activeTimetableId.value }
                                        ?.term ?: "1261"

                                    // Create the new timetable entry and add it to the list
                                    val newTimetable = Timetable(
                                        id      = newId,
                                        name    = newName,
                                        term    = newTerm,
                                        courses = schedule
                                    )
                                    AppState.timetables.add(newTimetable)

                                    // Make the new timetable active and sync scheduledCourses
                                    AppState.activeTimetableId.value = newId
                                    AppState.scheduledCourses.clear()
                                    AppState.scheduledCourses.addAll(schedule)

                                    // Persist to Firestore
                                    CourseRepository.saveAllTimetables(
                                        AppState.timetables.toList(),
                                        newId
                                    )
                                }
                                showScheduleDialog = false
                                showAppliedDialog = true
                            },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryYellow, contentColor = Color.Black)
                        ) {
                            Text("Apply to Schedule", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showNoResultsDialog) {
        AlertDialog(
            onDismissRequest = { showNoResultsDialog = false },
            title = { Text("No Schedules Found") },
            text = { Text("Could not build a conflict-free timetable that includes all selected courses. Make sure the chosen courses have non-overlapping sections, or try relaxing filters such as 'Avoid Early Classes' or 'Cluster Days'.") },
            confirmButton = { TextButton(onClick = { showNoResultsDialog = false }) { Text("OK") } }
        )
    }

    // ── Early-class conflict dialog ───────────────────────────────────────────
    // Shown when avoidEarlyClasses is on but one or more selected courses have
    // NO section at or after the cutoff hour — making the preference impossible
    // to satisfy.  We list the offending courses so the user knows exactly what
    // to fix (deselect the course, pin a later section, or lower the cutoff).
    if (showEarlyConflictDialog) {
        val cutoffLabel = if (earlyClassCutoff == 12) "12:00 PM" else "${earlyClassCutoff}:00 AM"
        val courseList  = earlyConflictCourses.joinToString("\n") { "  \u2022 $it" }
        AlertDialog(
            onDismissRequest = { showEarlyConflictDialog = false },
            title = { Text("Early Class Conflict") },
            text = {
                Text(
                    "You set \"No classes before $cutoffLabel\", but the following " +
                            "course(s) only have sections that start before that time — " +
                            "so no valid timetable can be built:\n\n$courseList\n\n" +
                            "To fix this, you can:\n" +
                            "  • Deselect the conflicting course(s)\n" +
                            "  • Lower the early-class cutoff time\n" +
                            "  • Turn off \"Avoid Early Classes\""
                )
            },
            confirmButton = {
                Button(
                    onClick = { showEarlyConflictDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor   = Color.Black
                    )
                ) { Text("Got it") }
            }
        )
    }

    // ── Max-daily-hours conflict dialog ─────────────────────────────────────────
    // Shown when a selected course has no section short enough to fit within the
    // chosen daily cap — making it physically impossible to place that course.
    if (showDailyHoursConflictDialog) {
        val capLabel   = "${maxDailyHours.toInt()} hour${if (maxDailyHours.toInt() == 1) "" else "s"}"
        val courseList = dailyHoursConflictCourses.joinToString("\n") { "  \u2022 $it" }
        AlertDialog(
            onDismissRequest = { showDailyHoursConflictDialog = false },
            title = { Text("Daily Hours Conflict") },
            text = {
                Text(
                    "You set a maximum of $capLabel per day, but the following " +
                            "course(s) have sections that are individually longer than that — " +
                            "so no valid timetable can be built:\n\n$courseList\n\n" +
                            "To fix this, you can:\n" +
                            "  \u2022 Deselect the conflicting course(s)\n" +
                            "  \u2022 Increase the maximum daily hours\n" +
                            "  \u2022 Pin a shorter section for the course"
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDailyHoursConflictDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        contentColor   = Color.Black
                    )
                ) { Text("Got it") }
            }
        )
    }

    if (showAppliedDialog) {
        AlertDialog(
            onDismissRequest = { showAppliedDialog = false },
            title = { Text("✅ Schedule Applied") },
            text = { Text("A new timetable has been created with the generated schedule and set as active.") },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Avoid Early Classes", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (avoidEarlyClasses)
                                        "Avoiding classes before ${earlyClassCutoff}:00"
                                    else
                                        "Prefer classes starting later in the day",
                                    fontSize = 13.sp, color = Color.Gray
                                )
                            }
                            Switch(
                                checked = avoidEarlyClasses,
                                onCheckedChange = { avoidEarlyClasses = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = primaryYellow,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.LightGray
                                )
                            )
                        }

                        // Cutoff time picker — only visible when the toggle is on
                        if (avoidEarlyClasses) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "\"Early\" means before ${earlyClassCutoff}:00",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Hour buttons: 7 AM through 12 PM
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(7, 8, 9, 10, 11, 12).forEach { hour ->
                                    val isChosen = earlyClassCutoff == hour
                                    OutlinedButton(
                                        onClick = { earlyClassCutoff = hour },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isChosen) primaryYellow else Color.Transparent,
                                            contentColor = Color.Black
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isChosen) primaryYellow else Color(0xFFCCCCCC)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            "${hour}AM".replace("12AM", "12PM"),
                                            fontSize = 11.sp,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                        Text("Select one or more days to concentrate classes on", fontSize = 13.sp, color = Color.Gray)
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
                            onValueChange = { maxDailyHours = it.toInt().toFloat() },
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

            // ── My Courses ────────────────────────────────────────────────────
            item {
                Text("My Courses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                if (selectedCourseCodes.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No courses added yet. Pick courses from Add Courses below.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val allCached = courseCache
                        .filter { it.key.startsWith("$selectedTerm||") }
                        .values.flatten()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedCourseCodes.sorted().forEach { code ->
                                val courseTitle = allCached.firstOrNull { it.code == code }?.title
                                // Collect all pinned sections for this course
                                val pinnedForCode = selectedSections
                                    .filter { it.key.startsWith("$code||") }
                                    .values.toList()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF9E6))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(code, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            if (courseTitle != null) {
                                                Text(courseTitle, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEEEEEE))
                                                .clickable {
                                                    selectedCourseCodes.remove(code)
                                                    // Also clear any pinned sections for this course
                                                    selectedSections.keys
                                                        .filter { it.startsWith("$code||") }
                                                        .forEach { selectedSections.remove(it) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✕", fontSize = 11.sp, color = Color(0xFF666666), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    // Show pinned sections underneath the course row
                                    if (pinnedForCode.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        pinnedForCode.forEach { sec ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${sec.component}  •  ${sec.days.joinToString(",")} ${sec.startTime}–${sec.endTime}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF555555)
                                                )
                                                // Unpin button (×) — removes just this section pin
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFDDDDDD))
                                                        .clickable {
                                                            selectedSections.remove("$code||${sec.componentType}")
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("×", fontSize = 10.sp, color = Color(0xFF444444))
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Any section (tap below to pin one)",
                                            fontSize = 11.sp,
                                            color = Color(0xFFAAAAAA)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Add Courses ────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Courses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (selectedTermLabel.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFF9E6), RoundedCornerShape(20.dp))
                                .border(1.dp, primaryYellow, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = selectedTermLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF7A6000)
                            )
                        }
                    }
                }
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

                    // Group sections by componentType so we can show LEC / TUT / LAB separately
                    val sectionsByType = remember(group.sections) {
                        group.sections.groupBy { it.componentType }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFFF9E6) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column {
                            // ── Header row: checkbox + course info ───────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) {
                                            selectedCourseCodes.remove(group.code)
                                            selectedSections.keys
                                                .filter { it.startsWith("${group.code}||") }
                                                .forEach { selectedSections.remove(it) }
                                        } else {
                                            selectedCourseCodes.add(group.code)
                                        }
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedCourseCodes.add(group.code)
                                        } else {
                                            selectedCourseCodes.remove(group.code)
                                            selectedSections.keys
                                                .filter { it.startsWith("${group.code}||") }
                                                .forEach { selectedSections.remove(it) }
                                        }
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
                                    Text(
                                        "${group.units} credits • ${group.sections.size} sections",
                                        fontSize = 12.sp, color = Color.Gray
                                    )
                                }
                            }

                            // ── Section picker — only shown when the course is selected ──
                            if (isSelected) {
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        "Pin a section (optional — leave blank to let the AI choose):",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    sectionsByType.forEach { (compType, sections) ->
                                        val pinKey   = "${group.code}||$compType"
                                        val pinned   = selectedSections[pinKey]

                                        Text(compType, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF444444))
                                        Spacer(modifier = Modifier.height(4.dp))

                                        sections.forEach { sec ->
                                            val isPinned = pinned?.classNumber == sec.classNumber
                                            val schedText = if (sec.days.isEmpty()) "TBA"
                                            else "${sec.days.joinToString(",")} ${sec.startTime}–${sec.endTime}"

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isPinned) primaryYellow.copy(alpha = 0.15f)
                                                        else Color.Transparent
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isPinned) primaryYellow else Color(0xFFDDDDDD),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        if (isPinned) {
                                                            // Tap pinned → unpin
                                                            selectedSections.remove(pinKey)
                                                        } else {
                                                            // Tap another → pin this one
                                                            selectedSections[pinKey] = sec
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sec.component, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    Text(schedText, fontSize = 11.sp, color = Color(0xFF666666))
                                                }
                                                if (isPinned) {
                                                    Text("✓ Pinned", fontSize = 11.sp,
                                                        color = Color(0xFF7A6000), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
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