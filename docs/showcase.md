# 🎓 Graphical Time Planner — UWaterloo Course Scheduler

> **Team InsertNameHere** · Khan Wahab · Julian Kwan · Jialun Li · Ariel Wong · Daniel Yim · Antony Zhao

---

## The Problem We Solved

Every UWaterloo student knows the dread of course selection season: juggling prerequisites, time conflicts, graduation requirements, and QUEST's clunky interface — all at once. Existing tools are either too rigid (official QUEST scheduler) or too manual (spreadsheets, sticky notes, hope).

**Graphical Time Planner** is a full-stack Android app that replaces that chaos with one intelligent, visual, AI-powered system.

---

## ✨ Key Features

### 📅 Visual Timetable
An interactive weekly calendar that shows your schedule at a glance. Add, remove, and reorganize courses across semesters with drag-and-drop ease. Export your finished schedule as a **PNG image** or **`.ics` calendar file** to import directly into Google Calendar or Apple Calendar.

### 🤖 Smart Schedule Generator (Assistant)
Build a wishlist of courses you *want* to take — the Assistant does the rest. Powered by a **Depth-First Search algorithm**, it automatically generates all valid, conflict-free schedule permutations from your wishlist. You pick the one you like and export it straight to your Timetable.

> Filter by preferences (time of day, days off, section types) to narrow down results to schedules that actually fit your life.

### 🧠 AI Agent Chatbot
This is where the app goes beyond a scheduler into a **personal academic assistant**. The AI chatbot can:

- 💬 Answer questions about your current schedule, conflicts, or workload
- 📄 **Read your transcript PDF** to understand what you've already completed
- 🔍 Check prerequisite chains for any course
- 📧 Draft emails to professors or academic advisors on your behalf
- 🛠️ **Directly modify your Firebase timetable** — add or remove courses through natural conversation

The chatbot is backed by a **Flask REST API** (Python) that securely proxies all Firebase operations and LLM calls, keeping your data safe while enabling rich agentic workflows.

### 🎓 Advisor Directory
Quick links to academic advisors by faculty, so you can get human help when the AI isn't enough.

### 🔔 Smart Notifications
Get notified about important course updates and schedule changes, even when the app is in the background.

---

## 🏗️ Architecture

```
Android App (Kotlin + Jetpack Compose)
        │
        ├── Firebase Firestore  ─── Course database (scraped from UWaterloo)
        │                       ─── User timetables & auth
        │
        └── Flask Backend (Python)
                ├── LLM Agent  ─── Chat, transcript parsing, recommendations
                └── Tools      ─── Firebase read/write, web search (SerpAPI)
```

**Tech Stack:**
- **Frontend**: Kotlin, Jetpack Compose, Material 3
- **Backend**: Python, Flask, LangChain-style tool-calling agent
- **Database**: Firebase Firestore + Firebase Auth
- **AI**: LLM via SiliconFlow API with tool-use support
- **Data**: Custom Python scrapers for UWaterloo's course catalog & schedule data

---

## 📸 App Screenshots

> *(See video demo link below)*

| Screen | Description |
|---|---|
| **Login / Register** | Secure Firebase Auth — sign up or log in |
| **My Timetable** | Visual weekly grid with color-coded courses |
| **Assistant** | Wishlist builder + DFS schedule generator |
| **AI Chatbot** | Chat interface with PDF upload support |
| **Advisor** | Faculty-sorted advisor directory with email links |

---

## 🎬 Demo Video

📽️ **[Watch the App Demo →](#)**

*(Link to be added — video walkthrough of all major features)*

---

## 💡 Why Vote For Us?

Most course planners stop at *showing* you a schedule. Ours goes further:

1. **It talks back** — the AI chatbot understands your history and goals, not just your current courses
2. **It acts** — the agent can modify your timetable mid-conversation, no manual steps needed
3. **It thinks ahead** — DFS-powered scheduling with preference filtering finds conflict-free options you'd never spot manually
4. **It's complete** — scraper → database → Android UI → AI backend, all built from scratch in one semester

We built every layer: the UWaterloo course data scraper, the Firestore schema, the Jetpack Compose UI, and the agentic Python backend. This isn't glue code over an API — it's a full-stack system designed around a real problem every student on this campus faces.

---

*Built with ☕ and determination by Team InsertNameHere — CS 446 / ECE 452, Winter 2025*
