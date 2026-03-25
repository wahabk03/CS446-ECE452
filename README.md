# InsertNameHere (Yep this is the team name)

# Graphical Time Planner

A graphical course scheduling and planning application for UWaterloo students.

## Members
- Khan Wahab (wahabk03)
- Kwan Julian (jkjk9696)
- Li Jialun (k424li)
- Wong Cheuk Him Ariel (arielwongch)
- Yim Sung Fung (DanielYimH)
- Zhao Antony (Antony-zdh)

## Documentation
- [Team contract](./docs/team-contract.md)
- [Meeting minutes](./docs/meetings/)

## App Structure & Features
- **Authentication**: Secure user login and registration.
- **Home Screen**: The central hub for navigating the application.
- **My Timetable**: A visual, interactive planner where users can manually search, add, and manage their courses across different semesters.
- **Assistant**: An automated scheduling tool. Users can build a wishlist of courses, and the Assistant will use a Depth-First Search (DFS) algorithm to generate conflict-free schedule permutations. Users can customize the subset size and export their preferred generated schedule directly to their Timetable.
- **AI Agent Chatbot**: An intelligent LLM-powered chat interface embedded within the app. Users can discuss schedule insights, ask about prerequisites, upload transcript PDFs, and get personalized recommendations. The AI has direct access to Firebase tools to safely build, modify, and clear the user's timetable automatically.
- **Flask Agent Backend**: A standalone Python REST API powering the LLM capabilities, securely proxying Firebase accesses and interacting dynamically with the Android client.

## Setup & Installation

### 1. Python Backend Setup (Scraping & Agent Server)
We recommend using [`uv`](https://github.com/astral-sh/uv) for fast Python package management.

1. **Create Virtual Environment:**
   ```bash
   uv venv
   # On Windows: .venv\Scripts\activate
   # On Mac/Linux: source .venv/bin/activate
   ```

2. **Install Dependencies:**
   ```bash
   uv pip install -r requirements.txt
   ```

### 2. Database Population
To populate the Firestore database with UWaterloo course data:

1. **Prerequisite:** You need a Firebase Service Account key.
   - Go to Firebase Console -> Project Settings -> Service Accounts.
   - Generate a new private key and save it as `parse/serviceAccountKey.json` (for the scraper) and another copy or symlink as `serviceAccountKey.json` in the root folder (for the AI Agent).
   - **Do not commit this file!**

2. **Run Scraper & Upload:**
   ```bash
   python parse/script_populate_db.py
   ```

### 3. Run the AI Agent Server
The Android app communicates with a local Flask server to fetch LLM responses and manage tools.
1. **Configure API Keys:** Make sure your `SILICONFLOW_API_KEY` or `SERPAPI_API_KEY` are configured properly in `agent/llm_config.py` (or through environment variables).
2. **Start the Flask Backend:**
   ```bash
   python agent/server_agent.py
   ```
   > Keep this terminal running in the background. The Android app connects to it securely at `10.0.2.2:5000`.

### 4. Android App Setup

#### Build from Command Line
To build and install the debug APK to your connected device or emulator:
```bash
# Windows
.\gradlew.bat installDebug

# Mac/Linux
./gradlew installDebug
```

#### Open in Android Studio
1. Launch **Android Studio**.
2. Select **Open**.
3. Navigate to and select the project root folder.
4. Wait for Gradle sync to complete.
5. Create a `google-services.json` in the `app/` directory (if not present) using your Firebase project configuration.
6. Click the green **Run** button (Shift+F10) to deploy to your emulator.