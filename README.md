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

## Setup & Installation

### 1. Python Backend Setup (Scraping)
We recommend using [`uv`](https://github.com/astral-sh/uv) for fast Python package management.

1. **Create Virtual Environment:**
   ```bash
   uv venv
   # On Windows: .venv\Scripts\activate
   # On Mac/Linux: source .venv/bin/activate
   ```

2. **Install Dependencies:**
   ```bash
   uv pip install -r parse/requirements.txt
   ```

### 2. Database Population
To populate the Firestore database with UWaterloo course data:

1. **Prerequisite:** You need a Firebase Service Account key.
   - Go to Firebase Console -> Project Settings -> Service Accounts.
   - Generate a new private key and save it as `parse/serviceAccountKey.json`.
   - **Do not commit this file!**

2. **Run Scraper & Upload:**
   ```bash
   python parse/script_populate_db.py
   ```

### 3. Android App Setup

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