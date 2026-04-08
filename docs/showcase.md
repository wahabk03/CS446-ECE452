# 🎓 Graphical Time Planner — UWaterloo Course Scheduler

> **Team InsertNameHere** · Khan Wahab · Jialun Li · Daniel Yim · Julian Kwan · Ariel Wong · Antony Zhao  
> *CS 446 / ECE 452 — Winter 2026*

---

## The Problem We Solved

Every UWaterloo student knows the dread of course selection season: juggling prerequisites, time conflicts, graduation requirements, and QUEST's clunky interface — all at once. Existing tools are either too rigid or too manual.

**Graphical Time Planner** is a full-stack Android app that replaces that chaos with one intelligent, visual, AI-powered system — built from scratch over 12 weeks.

---

## ✨ Key Features

### 📅 Visual Timetable
An interactive, color-coded weekly grid that shows your schedule at a glance. The app supports **multiple named timetables per term** so you can compare options side by side. Automatic conflict detection warns you the moment two courses overlap. When you're happy with your schedule, export it as a **PNG image** to share with friends or advisors.

### 🤖 AI-Powered Schedule Generator
Build a wishlist of courses you want to take, then let the generator do the work. A **genetic algorithm** explores the space of valid schedule permutations and surfaces the best ones based on your personal preferences:

- ☀️ Avoid early morning classes
- ⏱️ Minimize gaps between lectures
- 📆 Cluster classes onto fewer days
- 🕐 Cap maximum daily hours

Pin preferred sections, tweak the weights, and export your favorite result directly into your timetable — no copy-pasting.

### 🧠 AI Academic Advisor Chatbot
This is where the app goes well beyond a scheduler. The LLM-powered chatbot has **direct tool access** to your data and can:

- 💬 Answer questions about courses, prerequisites, and graduation requirements
- 📄 **Parse your uploaded transcript PDF** to understand your academic history
- 🔍 Pull real student reviews from **UW Flow**
- 🛠️ **Directly create, modify, and clear your timetable** — no manual steps needed
- 📧 Draft contextual emails to your academic advisor, with your schedule attached

Responses stream in real time. Chat sessions are saved with full history — create, rename, and revisit sessions at any time.

### 🔔 Push Notifications
The app monitors the UWaterloo course database every 6 hours and sends **Firebase Cloud Messaging** alerts if any of your enrolled courses change — time, location, section cancellation, or new section added. On logout, all topic subscriptions are automatically cleared so notifications never leak to the next user on the device.

### 🎓 Advisor Finder
Look up academic advisors by program and year level. Select an advisor and the AI generates a **personalized email draft** on your behalf — optionally including your current timetable for context.

---

## 🎬 App Demo & User Scenarios

Three short videos walk through the app's major features. Each can be watched independently.

---

### 📹 Video 1 — Course Search & Preference-Based Schedule Generation

📽️ **[Watch Video 1 →](https://www.youtube.com/watch?v=-LEMNJkuF4o&t=1s)**

Meet **Alex**, a 2B Software Engineering student building their Winter 2026 timetable. They have a few required courses locked in and a wishlist of electives — but no idea how to fit everything without early mornings or a fragmented week.

**Step 1 — Search and add courses manually**  
Alex opens the Course screen, selects the Winter 2026 term, and searches for `ECE 222`. They browse the available sections, pick one, and tap Add. The app instantly checks for conflicts — when Alex accidentally picks an overlapping section for `MATH 213`, a warning appears immediately. They swap to a compatible section and the grid updates.

**Step 2 — Build a wishlist for electives**  
Alex heads to the **Assistant** tab and adds `ECON 101` and `PSYCH 207` to their wishlist. They pin a preferred lecture section for one course but leave the other open for the generator to decide.

**Step 3 — Set preferences and generate**  
Alex configures their preferences: no classes before 9 AM, minimize gaps between lectures, cluster everything onto four days, and cap at 6 hours per day. They hit Generate — the genetic algorithm runs and surfaces a ranked list of conflict-free timetable permutations. Alex browses the top results, picks their favourite, and exports it directly to their timetable with one tap.

---

### 📹 Video 2 — Push Notifications & Advisor Email Generation

📽️ **[Watch Video 2 →](https://www.youtube.com/shorts/CSBcpiZOHYg)**

Two weeks into the term, the app keeps Alex informed without any effort on their part.

**Push Notifications**  
The app polls the UWaterloo course database every 6 hours. When `ECE 222 LEC 001` moves to a new room, Alex receives a Firebase Cloud Messaging notification immediately — no need to check QUEST. The notification includes the course, what changed, and the new details.

**Finding an Advisor and Drafting an Email**  
Alex wants to request a course substitution but isn't sure how to phrase it. They open the **Advisor Finder**, select Engineering and their year level, and the app surfaces their academic advisor with contact details. Alex taps "Generate Email" — the AI drafts a professional, contextual email explaining the substitution request. Alex reviews the draft, makes a small edit, and copies it straight to their mail app.

---

### 📹 Video 3 — AI Academic Advisor Chatbot

📽️ **[Watch Video 3 →](https://www.youtube.com/watch?v=e41-xI4eUeo)**

Alex has bigger questions that need more than a search box.

**Step 1 — Upload transcript and check prerequisites**  
Alex uploads their transcript PDF directly in the chat. They ask: *"Have I completed the prerequisites for CS 341?"* The agent reads the transcript, queries the course database for the full prerequisite chain, and replies with a clear breakdown — `CS 240` and `MATH 239` are both satisfied.

**Step 2 — Get course recommendations**  
Alex asks: *"What are some good technical electives for a 3A SE student that aren't too heavy? Check UW Flow reviews."* The agent searches UW Flow, weighs ratings and review sentiment, and comes back with three recommendations with reasoning.

**Step 3 — Let the AI modify the timetable directly**  
Convinced by the recommendation, Alex says: *"Add ECE 358 LEC 001 to my timetable."* The agent calls its `add_course` tool server-side, writes directly to Firebase, and confirms: *"Done — ECE 358 LEC 001 has been added. No conflicts detected."* Alex switches to the Home screen and sees it already there — no manual steps, no copy-pasting.

---

## 🏗️ Architecture

The app uses two architectural styles working in concert:

**Client-Server** — The Android client handles all UI and direct Firestore CRUD. An AI chat interaction goes: Android → Flask backend (Python, port 5000) → LLM with tool access → Firestore write → streamed JSON events back to Android. All timetable mutations from the AI are executed server-side with the Firebase UID injected by the backend, so clients can never spoof write operations.

**MVVM** — Inside the Android app, `AppState` and `ChatStateManager` act as reactive ViewModels using Compose's `mutableStateOf`. Screens observe state and recompose automatically — when the AI chatbot adds a course via a server tool, the timetable view updates instantly with no coordination code needed. `CourseRepository` and `ChatRepository` encapsulate all Firestore logic behind a clean Facade, so no screen ever touches a Firestore collection path directly.

```
Android App (Kotlin + Jetpack Compose + MVVM)
        │
        ├── Firebase Firestore  ── 61 UWaterloo subjects, user timetables, profiles, chat
        ├── Firebase Auth       ── Email/password authentication
        ├── Firebase FCM        ── Push notifications for course changes
        │
        └── Flask Backend (Python)
                ├── LLM Agent (SiliconFlow API)
                └── Tools: query courses · read transcript · add/remove courses
                            browse UW Flow · web search (SerpAPI) · draft emails
```

---

## 📊 By the Numbers

| Metric | Value |
|---|---|
| UWaterloo subjects supported | **61** |
| Functional requirements implemented | **20** |
| AI chatbot tools | **8+** (query, add, delete, clear timetable, browse, search, email, transcript) |
| Architectural patterns | **2** (Client-Server + MVVM) |
| Design patterns | **2** (Facade + Observer) |
| Development timeline | **12 weeks** |

---

## 💡 Why Vote For Us?

Most schedulers stop at *showing* you a timetable. Ours goes three steps further:

1. **It generates** — a genetic algorithm finds optimal, conflict-free schedules from your wishlist with configurable preference weights, not just brute force
2. **It talks** — an LLM chatbot understands your transcript, your goals, and your graduation requirements in natural language
3. **It acts** — the AI agent modifies your actual timetable through server-side tools mid-conversation, with no manual steps from you

Every layer was built from scratch: the UWaterloo course scraper, the Firestore schema, the Jetpack Compose UI, the genetic algorithm, and the agentic Python backend with streaming tool-call responses.

---

*Built with ☕ and determination by Team InsertNameHere — CS 446 / ECE 452, Winter 2026*
