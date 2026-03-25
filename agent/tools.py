import os
import requests
import base64
import firebase_admin
from firebase_admin import credentials, firestore
from llm_config import SERPAPI_API_KEY

# Initialize firebase admin if not already initialized
if not firebase_admin._apps:
    cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)


def read_uploaded_file(file_path: str) -> str:
    """
    Read the contents of an uploaded file. 
    Since we are using a text model for tool-calling, we extract the PDF text using pypdf.
    """
    file_path = os.path.expanduser(file_path)
    print(f"Reading file from {file_path}")
    
    if not os.path.exists(file_path):
        return f"Error: File {file_path} not found."
        
    ext = os.path.splitext(file_path)[1].lower()
    
    if ext == '.pdf':
        try:
            import pypdf
            text = ""
            with open(file_path, 'rb') as f:
                reader = pypdf.PdfReader(f)
                for page in reader.pages:
                    extracted = page.extract_text()
                    if extracted:
                        text += extracted + "\n"
            return text.strip() if text else "No text found in PDF."
        except ImportError:
            return "Error: The 'pypdf' library is required to parse PDF files. Install with 'pip install pypdf'."
        except Exception as e:
            return f"Error reading PDF: {e}"
    else:
        # Default to standard text reading
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                return f.read()
        except UnicodeDecodeError:
            return "Error: File format is not supported or cannot be read as standard text."

def browse_online(query: str) -> str:
    """
    Browses the web for missing prerequisite or course info using SerpAPI.
    """
    print(f"Browsing online for: {query}")
    if not SERPAPI_API_KEY or SERPAPI_API_KEY == "your_serpapi_key_here":
        return "Error: SERPAPI_API_KEY is not configured."
        
    url = "https://serpapi.com/search"
    params = {
        "engine": "google",
        "q": query,
        "api_key": SERPAPI_API_KEY
    }
    try:
        response = requests.get(url, params=params)
        response.raise_for_status()
        results = response.json()
        snippets = []
        for res in results.get("organic_results", [])[:3]:
            snippets.append(res.get("snippet", ""))
        return "\n".join(snippets) if snippets else "No results found."
    except Exception as e:
        return f"Error searching online: {e}"

def query_database_readonly(uid: str, query_type: str, target_id: str = None) -> dict:
    """
    Accesses the database with read-only permissions.
    - query_type='course_info': Reads from the global 'courses' collection. 'target_id' is the course doc id (e.g., '1255_ACTSC_221').
    - query_type='user_schedule': Reads the user's entire 'scheduledCourses' list from the 'users/{uid}' document.
    - query_type='user_assistant': Reads the user's saved wishlist and generated schedules from 'users/{uid}/assistant/{target_id}'.
    """
    print(f"Querying database - User: {uid}, Type: {query_type}, Target: {target_id}")
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}
        
    try:
        db = firestore.client()
        
        if query_type == "course_info":
            if not target_id:
                # To prevent context overflow, don't return the entire courses collection.
                return {"error": "target_id (course document ID like '1255_ACTSC_221') is required for course_info."}
            doc = db.collection("courses").document(target_id).get()
            if doc.exists:
                return {"course_data": doc.to_dict()}
            else:
                # Try finding courses that start with the prefix if it's incomplete
                # Basic workaround if AI doesn't know exact doc ID format
                docs = db.collection("courses").where("__name__", ">=", target_id).where("__name__", "<=", target_id + "\uf8ff").limit(5).stream()
                matches = {d.id: d.to_dict() for d in docs}
                if matches:
                    return {"course_data_matches": matches}
                return {"error": f"Course document {target_id} not found."}
                
        elif query_type == "user_schedule":
            doc = db.collection("users").document(uid).get()
            if doc.exists:
                data = doc.to_dict() or {}
                return {"scheduledCourses": data.get("scheduledCourses", [])}
            return {"scheduledCourses": [], "message": "User document not found or no schedule saved."}
            
        elif query_type == "user_assistant":
            if not target_id:
                # Without term, retrieve all terms they generated for
                docs = db.collection("users").document(uid).collection("assistant").stream()
                return {"assistant_data": {d.id: d.to_dict() for d in docs}}
            
            doc = db.collection("users").document(uid).collection("assistant").document(target_id).get()
            if doc.exists:
                return {"assistant_data": doc.to_dict()}
            return {"message": "No assistant data found. The collection for this term is currently empty.", "assistant_data": {}}
            
        else:
            return {"error": f"Unknown query_type: {query_type}"}

    except Exception as e:
        return {"error": str(e)}

def add_course_to_timetable(uid: str, term: str, course_code: str, sections: list) -> dict:
    """
    Adds a course's specific sections to the user's timetable in the database.
    """
    import re
    def parse_time_date(time_str):
        if not time_str or time_str.strip() == "" or time_str.strip() == "TBA":
            return {"startHour": 0, "startMinute": 0, "endHour": 0, "endMinute": 0, "days": []}
            
        match = re.search(r"(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})", time_str)
        if not match:
            return {"startHour": 0, "startMinute": 0, "endHour": 0, "endMinute": 0, "days": []}
            
        start_h, start_m, end_h, end_m = [int(g) for g in match.groups()]
        
        if start_h < 8: start_h += 12
        if end_h < 8: end_h += 12
        if end_h < start_h: end_h += 12
            
        rest = time_str[match.end():].strip()
        days_letters = ""
        for c in rest:
            if c.isalpha():
                days_letters += c
            else:
                break
                
        days = []
        i = 0
        while i < len(days_letters):
            if days_letters[i] == 'M': days.append("Mon"); i += 1
            elif days_letters[i] == 'T':
                if i + 1 < len(days_letters) and days_letters[i+1] == 'h':
                    days.append("Thu"); i += 2
                else: days.append("Tue"); i += 1
            elif days_letters[i] == 'W': days.append("Wed"); i += 1
            elif days_letters[i] == 'F': days.append("Fri"); i += 1
            elif days_letters[i] == 'S':
                if i + 1 < len(days_letters) and days_letters[i+1] == 'u':
                    days.append("Sun")
                else: days.append("Sat")
                i += 1
            else: i += 1
                
        return {"startHour": start_h, "startMinute": start_m, "endHour": end_h, "endMinute": end_m, "days": days}

    print(f"Adding course to timetable - User: {uid}, Term: {term}, Course: {course_code}, Sections: {sections}")
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}
    
    try:
        db = firestore.client()
        parts = course_code.split(" ")
        if len(parts) >= 2:
            subject = parts[0]
            catalog = " ".join(parts[1:])
        else:
            return {"error": "Invalid course_code format. Expecting e.g. 'CS 136'"}

        doc_id = f"{term}_{subject}_{catalog}"
        course_doc = db.collection("courses").document(doc_id).get()
        if not course_doc.exists:
            return {"error": f"Course {course_code} not found in database for term {term}. Does this course exist?"}
            
        course_data = course_doc.to_dict()
        available_sections = course_data.get("sections", [])
        
        # map for matching
        section_map = {s["component"]: s for s in available_sections}
        
        user_ref = db.collection("users").document(uid)
        doc = user_ref.get()
        data = doc.to_dict() or {} if doc.exists else {}
        scheduled_courses = data.get("scheduledCourses", [])
        
        added_count = 0
        added_components = []
        for sec_req in sections:
            if sec_req in section_map:
                sec_data = section_map[sec_req]
                t_info = parse_time_date(sec_data.get("time_date", ""))
                
                new_course = {
                    "code": str(course_code),
                    "title": str(course_data.get("title", "")),
                    "term": str(term),
                    "units": str(course_data.get("units", "0.5")),
                    "classNumber": str(sec_data.get("class", "")),
                    "component": str(sec_data.get("component", "")),
                    "days": t_info["days"],
                    "startHour": int(t_info["startHour"]),
                    "startMinute": int(t_info["startMinute"]),
                    "endHour": int(t_info["endHour"]),
                    "endMinute": int(t_info["endMinute"]),
                    "location": str(sec_data.get("location", ""))
                }
                
                prefix = sec_req.split(" ")[0] if " " in sec_req else sec_req
                scheduled_courses = [
                    c for c in scheduled_courses 
                    if not (c.get("code") == course_code and c.get("term") == term and c.get("component", "").startswith(prefix))
                ]
                
                scheduled_courses.append(new_course)
                added_count += 1
                added_components.append(sec_req)
            else:
                return {"error": f"Section component '{sec_req}' not found for course {course_code}. Available components: {list(section_map.keys())}"}
                
        user_ref.set({"scheduledCourses": scheduled_courses}, merge=True)
        return {"message": f"Successfully added/updated sections: {', '.join(added_components)} for {course_code} in term {term}."}
    except Exception as e:
        return {"error": str(e)}

def delete_course_from_timetable(uid: str, term: str, course_code: str) -> dict:
    """
    Deletes a specific course from the user's timetable.
    """
    print(f"Deleting course from timetable - User: {uid}, Term: {term}, Course: {course_code}")
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}
    
    try:
        db = firestore.client()
        user_ref = db.collection("users").document(uid)
        doc = user_ref.get()
        
        if not doc.exists:
            return {"message": "User document not found."}
            
        data = doc.to_dict() or {}
        scheduled_courses = data.get("scheduledCourses", [])
        
        original_length = len(scheduled_courses)
        scheduled_courses = [c for c in scheduled_courses if not (str(c.get("term")) == str(term) and c.get("code") == course_code)]
        
        if len(scheduled_courses) == original_length:
            return {"message": f"Course {course_code} for term {term} is not present in the timetable."}
            
        user_ref.set({"scheduledCourses": scheduled_courses}, merge=True)
        return {"message": f"Successfully deleted {course_code} from timetable for term {term}."}
    except Exception as e:
        return {"error": str(e)}

def clear_timetable(uid: str, term: str) -> dict:
    """
    Clears the user's timetable for a specific term.
    """
    print(f"Clearing timetable - User: {uid}, Term: {term}")
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}
    
    try:
        db = firestore.client()
        user_ref = db.collection("users").document(uid)
        doc = user_ref.get()
        
        if not doc.exists:
            return {"message": "User document not found."}
            
        data = doc.to_dict() or {}
        scheduled_courses = data.get("scheduledCourses", [])
        
        scheduled_courses = [c for c in scheduled_courses if str(c.get("term")) != str(term)]
        
        user_ref.set({"scheduledCourses": scheduled_courses}, merge=True)
        return {"message": f"Successfully cleared timetable for term {term}."}
    except Exception as e:
        return {"error": str(e)}

def show_timetable_button() -> dict:
    """
    Signals the UI to show a button allowing the user to view their timetable changes.
    """
    return {"message": "Timetable button activated for the user."}

TOOLS_SCHEMA = [
    {
        "type": "function",
        "function": {
            "name": "browse_online",
            "description": "Browses the web for missing prerequisite or course info using SerpAPI.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "The search query to look up on the web"
                    }
                },
                "required": ["query"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "query_database_readonly",
            "description": "Accesses the timetable database to retrieve user schedules, academic assistant history, or specific course info.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query_type": {
                        "type": "string",
                        "enum": ["course_info", "user_schedule", "user_assistant"],
                        "description": "Determine what to fetch. 'course_info': global courses. 'user_schedule': the user's primary saved schedule. 'user_assistant': the user's generated timetables/wishlists."
                    },
                    "target_id": {
                        "type": "string",
                        "description": "The specific document ID. Required for 'course_info' (e.g. '1255_ACTSC_221'). For 'user_assistant', it is the term code (e.g., '1255'). Optional/ignored for 'user_schedule'."
                    }
                },
                "required": ["query_type"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "add_course_to_timetable",
            "description": "Adds specific sections of a course to the user's timetable. Query course_info first to find exact section components before adding.",
            "parameters": {
                "type": "object",
                "properties": {
                    "term": { "type": "string", "description": "The term to add the course to (e.g., '1255')." },
                    "course_code": { "type": "string", "description": "The course code to add (e.g., 'ACTSC 221')." },
                    "sections": { 
                        "type": "array",
                        "items": { "type": "string" },
                        "description": "A list of exact section component names. Query course_info first to retrieve available components (e.g., 'LEC 001', 'TUT 101', 'LAB 011', 'CLN 002', etc.). Include all necessary components for the course."
                    }
                },
                "required": ["term", "course_code", "sections"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "delete_course_from_timetable",
            "description": "Deletes a specific course from the user's timetable.",
            "parameters": {
                "type": "object",
                "properties": {
                    "term": { "type": "string", "description": "The term from which to delete the course (e.g., '1255')." },
                    "course_code": { "type": "string", "description": "The course code to delete (e.g., 'ACTSC 221')." }
                },
                "required": ["term", "course_code"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "clear_timetable",
            "description": "Clears the user's entire timetable for a given term.",
            "parameters": {
                "type": "object",
                "properties": {
                    "term": { "type": "string", "description": "The term to clear (e.g., '1255')." }
                },
                "required": ["term"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "show_timetable_button",
            "description": "Triggers a UI button that navigates the user to their timetable. Call this immediately after adding or modifying a course so the user can review their new schedule.",
            "parameters": {
                "type": "object",
                "properties": {},
                "required": []
            }
        }
    }
]
