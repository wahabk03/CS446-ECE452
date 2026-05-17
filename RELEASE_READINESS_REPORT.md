# Release Readiness Report

Last updated: 2026-05-17

This document tracks the current issues found during a full repository scan. Use the checkboxes as a lightweight release checklist: when an item is fixed, check it off and add a short note or PR/commit reference.

## Executive Summary

The app now builds with `./gradlew assembleDebug`, but it is not yet ready for public store distribution without a few production-readiness fixes. The Android client is generally functional, but the AI agent path is still configured as a local/demo backend (`http://10.0.2.2:5000`) and the Flask server trusts a client-supplied `uid`. That is the biggest security and billing risk because anyone who can reach the backend could impersonate users and spend API quota.

Android lint was also run with `./gradlew lintDebug`. It currently fails with 4 errors, all caused by `NotificationHelper.createChannel()` using Android 8.0+ notification channel APIs while `minSdk` is 24.

## App Workflow Notes

- **Login/Register:** Firebase Authentication signs users in and saves display names to both Firebase Auth and Firestore.
- **Home/Timetable:** Loads all user timetables from `users/{uid}`, sets the active timetable, renders a weekly grid, detects course changes, supports export, create/delete/clear timetable, and subscribes to FCM course topics.
- **Courses:** Fetches Firestore course sections by subject and term, lets users select required components, saves timetable changes, and subscribes to course update topics.
- **Assistant/Preference:** Builds a course wishlist, pins sections, generates conflict-free schedules, applies a generated schedule to the active timetable, and persists assistant state under `users/{uid}/assistant/{term}`.
- **Chatbot:** Stores chat sessions under `users/{uid}/agent/history/sessions`, streams tool status from the Flask backend, supports transcript/file upload, and can ask the agent to mutate timetables.
- **Advisor:** Loads user profile/advisor mapping, generates advisor email drafts through the agent backend, optionally appends timetable details, then launches an email client.
- **Python backend:** Flask exposes `/chat`, `/chat_stream`, `/summarize`, and `/generate_email`; it calls the LLM provider, SerpAPI, Firestore Admin SDK, and timetable mutation tools.
- **Parser/admin scripts:** Scrape Waterloo schedule data, populate Firestore course/program/advisor collections, and send FCM topic notifications for detected course changes.

## Current Issues

### Critical

- [x] **Backend does not verify Firebase identity tokens**
  - **Files:** `app/src/main/java/com/example/graphicaltimeplanner/AgentApi.kt`, `agent/server_agent.py`, `agent/tools.py`
  - **Problem:** Android sends `uid` in JSON, and Flask trusts it. A malicious caller could send another user's UID, read/modify that user's timetable through tools, and spend LLM/search budget.
  - **Fix proposal:** Send `FirebaseAuth.currentUser.getIdToken(false)` as `Authorization: Bearer <token>` from Android. On Flask, verify with `firebase_admin.auth.verify_id_token`, derive `uid` from the verified token, and ignore any client-supplied UID. Require auth for `/chat`, `/chat_stream`, `/summarize`, and `/generate_email`.
  - **Fix note:** Implemented on branch `release-readiness-critical-fixes`. Android sends Firebase bearer tokens; Flask verifies them and derives `uid` server-side. Firebase Admin can now initialize on Railway with `FIREBASE_SERVICE_ACCOUNT_JSON` or `GOOGLE_APPLICATION_CREDENTIALS`.

- [x] **Agent backend is local/demo-only and uses cleartext HTTP**
  - **Files:** `AgentApi.kt`, `AndroidManifest.xml`, `README.md`
  - **Problem:** URLs are hardcoded to `http://10.0.2.2:5000`; manifest enables `android:usesCleartextTraffic="true"`. This works on emulator, but not for production users and is not acceptable for App/Play Store release.
  - **Fix proposal:** Add build flavors or `BuildConfig.AGENT_BASE_URL`. Use `10.0.2.2` only in debug, and a deployed HTTPS backend in release. Remove global cleartext traffic in release; if needed, use a debug-only network security config.
  - **Fix note:** Implemented with `BuildConfig.AGENT_BASE_URL`. Debug uses `http://10.0.2.2:5000`; release reads `AGENT_BASE_URL` from a Gradle property or environment variable and disables cleartext traffic. For Railway release builds, set `AGENT_BASE_URL=https://your-service.up.railway.app`.

- [x] **No quota/rate limiting around LLM and SerpAPI calls**
  - **Files:** `agent/server_agent.py`, `agent/tools.py`
  - **Problem:** Any valid or invalid user can generate repeated LLM calls, tool loops, uploads, summaries, and web searches. This can create runaway cost.
  - **Fix proposal:** Add per-user daily/monthly quotas, request rate limits, max tool calls per request, max concurrent requests per UID, and server-side usage logging. Return a friendly "daily limit reached" message.
  - **Fix note:** Implemented an in-memory per-user request rate limit, daily cost quota, and max tool-call budget. Railway env knobs: `AGENT_RATE_LIMIT_WINDOW_SECS`, `AGENT_RATE_LIMIT_MAX_REQUESTS`, `AGENT_DAILY_QUOTA_MAX_COST`, `AGENT_MAX_TOOL_CALLS_PER_REQUEST`, `AGENT_COST_SUMMARIZE`, `AGENT_COST_GENERATE_EMAIL`, `AGENT_COST_CHAT`, and `AGENT_COST_CHAT_STREAM`.

### High

- [ ] **Android lint fails on notification channel API level**
  - **File:** `app/src/main/java/com/example/graphicaltimeplanner/NotificationHelper.kt`
  - **Problem:** `NotificationChannel` and `createNotificationChannel` require API 26, but `minSdk` is 24.
  - **Fix proposal:** Wrap channel creation with `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { ... }`. Keep notification display code compatible with API 24+.

- [ ] **Debug Firebase placeholder is not a real release config**
  - **File:** `app/src/debug/google-services.json`
  - **Problem:** The placeholder allows debug builds only. Release builds need a real Firebase Android app config and matching package/SHA settings.
  - **Fix proposal:** Keep placeholder in `src/debug`; add the real `app/google-services.json` or release-only config through secure CI/secrets before publishing. Do not commit service account keys.

- [ ] **Sensitive and verbose logging**
  - **Files:** `CourseRepository.kt`, `CourseMessagingService.kt`, `agent/server_agent.py`, `agent/tools.py`, `agent/agent.py`
  - **Problem:** Logs include course/profile data, FCM token, tool responses, uploaded file paths/content-derived data, and API error bodies. This risks privacy leakage in production logs.
  - **Fix proposal:** Gate Android debug logs with `BuildConfig.DEBUG`, remove FCM token logging, replace backend `print` calls with structured logs that redact UID, tokens, uploaded content, and LLM response bodies.

- [ ] **Backend uploads have no size/type limits**
  - **Files:** `AgentApi.kt`, `agent/server_agent.py`, `agent/tools.py`
  - **Problem:** Android reads the entire file into memory and base64 encodes it; Flask decodes the entire payload into temp storage. Large files can crash the client/server or inflate LLM prompts.
  - **Fix proposal:** Restrict accepted MIME types to PDF/text, enforce client and server max size, truncate extracted text to a token/character budget, and reject encrypted/unparseable PDFs cleanly.

- [ ] **Firestore user document may grow too large**
  - **Files:** `CourseRepository.kt`, `ChatRepository.kt`, `agent/tools.py`
  - **Problem:** All timetables are stored as arrays inside `users/{uid}`. Firestore documents have a size limit; many timetables/courses can hit it. Chat sessions store all messages in one session document, which can also grow large.
  - **Fix proposal:** Move timetables to `users/{uid}/timetables/{timetableId}` and messages to `users/{uid}/agent/history/sessions/{sessionId}/messages/{messageId}` or enforce hard caps.

- [ ] **Agent timetable writes are non-transactional**
  - **Files:** `agent/tools.py`, `CourseRepository.kt`
  - **Problem:** Client saves and backend agent mutations both read-modify-write timetable arrays. Concurrent changes can overwrite each other.
  - **Fix proposal:** Use Firestore transactions for timetable mutations, or move courses into subcollections where individual course updates are atomic.

- [ ] **Release signing/minification not configured**
  - **File:** `app/build.gradle.kts`
  - **Problem:** Release has `isMinifyEnabled = false`, and no visible release signing setup. This is okay for class, not for store release.
  - **Fix proposal:** Configure release signing through local/CI secrets, enable R8/minify after testing, and add ProGuard/R8 keep rules if Firebase/serialization need them.

### Medium

- [ ] **Notification permission and channel UX need polish**
  - **Files:** `HomeScreen.kt`, `NotificationHelper.kt`
  - **Problem:** Permission is requested on home load without much context, and denied permission is silently ignored.
  - **Fix proposal:** Ask after the user adds courses or in profile notification settings, explain why notifications help, and show a settings route if denied.

- [ ] **No Terms/Privacy/AI disclaimer flow**
  - **Files:** UI screens, README/deployment docs
  - **Problem:** The app handles profile data, schedules, transcript uploads, advisor email drafting, and LLM-generated academic advice. Store review and user trust need clear disclosure.
  - **Fix proposal:** Add Privacy Policy, Terms, AI limitations disclaimer, data deletion instructions, and transcript-upload consent text.

- [ ] **Program/advisor data can become stale**
  - **Files:** `parse/script_populate_programs.py`, `CourseRepository.kt`, `AdvisorScreen.kt`
  - **Problem:** Advisor emails and programs are hardcoded in a script. University contacts change.
  - **Fix proposal:** Add `sourceUrl`, `lastVerifiedAt`, and a scheduled review process. Show fallback advisor only when exact mapping is missing.

- [ ] **Course subject list is hardcoded**
  - **File:** `CourseRepository.kt`
  - **Problem:** New or renamed subjects will not appear unless code is updated.
  - **Fix proposal:** Store subjects/terms in Firestore metadata generated by the scraper and cache them in the app.

- [ ] **Network error handling is generic**
  - **Files:** `AgentApi.kt`, `LoginScreen.kt`, `RegisterScreen.kt`, `CourseScreen.kt`
  - **Problem:** Several errors collapse to broad messages or expose raw localized backend errors.
  - **Fix proposal:** Normalize user-facing error states: offline, timeout, auth expired, backend unavailable, quota exceeded, invalid upload, and retry available.

- [ ] **Force unwraps can crash after state changes**
  - **Files:** `HomeScreen.kt`, `ProfileScreen.kt`, `AdvisorScreen.kt`, `ChatbotScreen.kt`
  - **Problem:** Several UI paths use `!!` after conditional rendering. Most are probably safe today, but recomposition or async state changes can break assumptions.
  - **Fix proposal:** Replace with local `val selected = selectedProgram ?: return@Button` patterns or render from non-null scoped variables.

- [ ] **Old screens are still compiled**
  - **Files:** `app/src/main/java/com/example/graphicaltimeplanner/oldScreens/*`
  - **Problem:** Old screens add lint warnings and maintenance noise, even if not navigated to.
  - **Fix proposal:** Remove them, move to documentation/examples, or exclude them from production source sets.

- [ ] **Dependency and lint warnings**
  - **Files:** `gradle/libs.versions.toml`, resource folders
  - **Problem:** Lint reports outdated dependencies, unused resources, missing monochrome launcher icon, default-locale formatting in old screen, and Compose primitive state hints.
  - **Fix proposal:** Fix functional lint errors first, then batch dependency/resource cleanup in a separate PR and rerun lint.

### Low

- [ ] **Branding still uses placeholder package/name**
  - **Files:** `app/build.gradle.kts`, `strings.xml`, Kotlin package paths
  - **Problem:** `applicationId = "com.example.graphicaltimeplanner"` and app name "Graphical Time Planner" read like a prototype.
  - **Fix proposal:** Choose final brand, package ID, icon, launcher label, store description, and screenshots before publishing.

- [ ] **Commercial analytics are missing**
  - **Files:** No dedicated analytics module found
  - **Problem:** It will be difficult to understand conversion, retention, expensive agent usage, and failure rates.
  - **Fix proposal:** Add privacy-preserving analytics events: registration, timetable created, course added, assistant generation, chat request, upload used, advisor email generated, quota reached, backend error.

## Suggested Fix Order

1. Fix `NotificationHelper` API guard and rerun `./gradlew lintDebug`.
2. Add production backend authentication with Firebase ID token verification.
3. Split debug/release backend URLs and remove release cleartext traffic.
4. Add quotas, rate limits, usage metering, and upload limits before any public beta.
5. Decide Firestore schema migration for timetables/chat history before user growth.
6. Add Privacy Policy, Terms, AI disclaimer, and data deletion flow.
7. Configure release signing, real Firebase release config, app icon/brand, and store metadata.
8. Clean logs, old screens, unused resources, and dependency warnings.

## Commercial Plan

### Positioning

Graphical Time Planner should be positioned as a Waterloo-focused schedule planner with an AI academic copilot. The core promise is not generic chat; it is saving students time when planning terms, comparing schedule options, understanding requirements, and preparing advisor conversations.

### Target Users

- **Free users:** Students who need timetable visualization, course search, and light schedule planning.
- **Power users:** Students planning multiple terms, degree requirements, co-op constraints, exchange terms, or heavy course loads.
- **Institution/club partners:** Student societies, advising groups, or campus organizations that may sponsor access for cohorts.

### Pricing Model

- **Free tier**
  - Manual timetable builder
  - Course search
  - Basic timetable export
  - Limited AI chat, for example 10 messages/month
  - No transcript upload or limited upload count

- **Student Plus**
  - Suggested price: CAD $3.99/month or CAD $19.99/year
  - Higher AI quota, for example 150 messages/month
  - Transcript/course-list upload
  - Advisor email drafting
  - Multi-term planning
  - Priority schedule generation

- **Pay-as-you-go AI credits**
  - Suggested price: CAD $2.99 for a credit pack
  - Useful for students who dislike subscriptions
  - Credits map directly to expensive actions: transcript analysis, long chats, web browsing, advisor draft generation

- **Sponsored access**
  - Offer bulk codes to student groups or departments.
  - Good for initial adoption and reduces individual payment friction.

### Cost Controls

- Require authenticated users for all AI endpoints.
- Track per-user usage: LLM calls, input/output tokens if provider returns them, SerpAPI searches, upload count, tool mutations.
- Use cheaper model defaults for summaries/email drafts and reserve stronger models for transcript/degree planning.
- Cache course/requirement lookups and common answers.
- Limit tool-call loops per request.
- Use short chat history summarization instead of sending full history forever.
- Add server-side prompt and upload truncation.

### Launch Strategy

- Start with a closed Waterloo beta using free tier plus manual invite codes.
- Measure activation: account created -> first course added -> first timetable saved -> first AI request.
- Offer Student Plus only after backend quotas and privacy docs are ready.
- Use "early supporter" annual pricing for the first cohort.
- Add referral credits: both users receive limited AI credits, not free unlimited access.

### Store and Trust Requirements

- Publish a clear privacy policy covering Firebase Auth, Firestore schedules/profile data, transcript uploads, AI provider processing, logs, retention, and deletion.
- Add an in-app AI disclaimer: advice may be wrong; official calendar/advisor guidance wins.
- Add account/data deletion instructions.
- Avoid claiming official University of Waterloo affiliation unless formally approved.

## Verification Log

- `./gradlew assembleDebug`: Passed after adding debug-only Firebase placeholder.
- `./gradlew lintDebug`: Failed with 4 `NewApi` errors in `NotificationHelper.kt`; report also listed 57 warnings and 7 hints.
