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
                    "uid": {
                        "type": "string",
                        "description": "The user ID (required)."
                    },
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
                "required": ["uid", "query_type"]
            }
        }
    }
]
