import os
import json
import hashlib
import logging
import requests
import firebase_admin
from firebase_admin import credentials, firestore
from google.cloud.firestore import transactional as _transactional
from llm_config import SERPAPI_API_KEY

_HTTP_SESSION = requests.Session()
_SERPAPI_TIMEOUT_SECS = 20
logger = logging.getLogger(__name__)
MAX_TIMETABLES_PER_USER = int(os.getenv("AGENT_MAX_TIMETABLES_PER_USER", "12"))
MAX_COURSES_PER_TIMETABLE = int(os.getenv("AGENT_MAX_COURSES_PER_TIMETABLE", "50"))


def _safe_uid(uid: str) -> str:
    return hashlib.sha256(str(uid).encode("utf-8")).hexdigest()[:10]


def _cap_timetables(timetables: list) -> list:
    capped = []
    for timetable in (timetables or [])[:MAX_TIMETABLES_PER_USER]:
        if isinstance(timetable, dict):
            copied = dict(timetable)
            copied["courses"] = (copied.get("courses") or [])[:MAX_COURSES_PER_TIMETABLE]
            capped.append(copied)
    return capped


def _resolve_active_timetable_index(timetables: list, active_id: str):
    for i, timetable in enumerate(timetables):
        if timetable.get("id") == active_id:
            return i
    if timetables:
        return len(timetables) - 1
    return -1


class _UserFacingError(Exception):
    """Non-retried error for user-visible rejection (quota exceeded, conflict, etc.)."""
    pass

# Initialize firebase admin if not already initialized
if not firebase_admin._apps:
    service_account_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    if service_account_json:
        cred = credentials.Certificate(json.loads(service_account_json))
    elif os.getenv("GOOGLE_APPLICATION_CREDENTIALS"):
        cred = credentials.ApplicationDefault()
    else:
        cred = credentials.Certificate("serviceAccountKey.json")
    firebase_admin.initialize_app(cred)


def read_uploaded_file(file_path: str) -> str:
    """
    Read the contents of an uploaded file. 
    Since we are using a text model for tool-calling, we extract the PDF text using pypdf.
    """
    file_path = os.path.expanduser(file_path)
    logger.info("Reading uploaded file ext=%s", os.path.splitext(file_path)[1].lower())
    
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
    logger.info("Browsing online query_length=%s", len(query or ""))
    if not SERPAPI_API_KEY or SERPAPI_API_KEY == "your_serpapi_key_here":
        return "Error: SERPAPI_API_KEY is not configured."
        
    url = "https://serpapi.com/search"
    params = {
        "engine": "google",
        "q": query,
        "api_key": SERPAPI_API_KEY
    }
    try:
        response = _HTTP_SESSION.get(url, params=params, timeout=_SERPAPI_TIMEOUT_SECS)
        response.raise_for_status()
        results = response.json()
        snippets = []
        for res in results.get("organic_results", [])[:3]:
            snippets.append(res.get("snippet", ""))
        return "\n".join(snippets) if snippets else "No results found."
    except Exception as e:
        return f"Error searching online: {e}"


def browse_uwflow(query: str) -> str:
    """
    Searches UW Flow specifically for course/professor review information.
    Returns concise results with title, link, and snippet.
    """
    logger.info("Browsing UW Flow query_length=%s", len(query or ""))
    if not SERPAPI_API_KEY or SERPAPI_API_KEY == "your_serpapi_key_here":
        return "Error: SERPAPI_API_KEY is not configured."

    url = "https://serpapi.com/search"
    params = {
        "engine": "google",
        "q": f"site:uwflow.com {query}",
        "api_key": SERPAPI_API_KEY
    }

    try:
        response = _HTTP_SESSION.get(url, params=params, timeout=_SERPAPI_TIMEOUT_SECS)
        response.raise_for_status()
        results = response.json()

        organic_results = results.get("organic_results", [])[:5]
        if not organic_results:
            return "No UW Flow results found."

        lines = []
        for i, res in enumerate(organic_results, start=1):
            title = res.get("title", "Untitled")
            link = res.get("link", "")
            snippet = res.get("snippet", "No snippet available.")
            lines.append(f"{i}. {title}\nURL: {link}\n{snippet}")

        return "\n\n".join(lines)
    except Exception as e:
        return f"Error searching UW Flow: {e}"

def query_database_readonly(uid: str, query_type: str, target_id: str = None) -> dict:
    """
    Accesses the database with read-only permissions.
    - query_type='course_info': Reads from the global 'courses' collection. 'target_id' is the course doc id (e.g., '1255_ACTSC_221').
        - query_type='user_schedule': Reads the user's timetable and profile summary from the 'users/{uid}' document.
    - query_type='user_assistant': Reads the user's saved wishlist and generated schedules from 'users/{uid}/assistant/{target_id}'.
        - query_type='major_graduation_requirement': Reads major-specific graduation requirement doc(s) from 'major_graduation_requirement'.
            If target_id is missing, attempts to use user's saved major/majorName.
    """
    logger.info("Querying database uid=%s type=%s target_present=%s", _safe_uid(uid), query_type, bool(target_id))
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}
        
    try:
        db = firestore.client()
        
        if query_type == "course_info":
            if not target_id:
                # To prevent context overflow, don't return the entire courses collection.
                return {"error": "target_id (course document ID like '1255_ACTSC_221') is required for course_info."}

            def _course_doc_id_candidates(raw_id: str):
                raw = (raw_id or "").strip()
                if not raw:
                    return []

                candidates = []

                def add(v: str):
                    if v and v not in candidates:
                        candidates.append(v)

                add(raw)
                add(raw.replace(" ", "_"))

                norm = raw.replace("_", " ")
                parts = [p for p in norm.split() if p]
                if len(parts) >= 3 and parts[0].isdigit():
                    term = parts[0]
                    subject = parts[1].upper()
                    catalog = "".join(parts[2:]).upper()
                    add(f"{term}_{subject}_{catalog}")
                    add(f"{term}_{subject} {catalog}")
                elif len(parts) == 2 and parts[0].isdigit():
                    term = parts[0]
                    rest = parts[1].upper()
                    subject = "".join(ch for ch in rest if ch.isalpha())
                    catalog = "".join(ch for ch in rest if ch.isdigit())
                    if subject and catalog:
                        add(f"{term}_{subject}_{catalog}")

                return candidates

            candidates = _course_doc_id_candidates(target_id)
            for candidate in candidates:
                doc = db.collection("courses").document(candidate).get()
                if doc.exists:
                    return {"course_data": doc.to_dict(), "resolved_target_id": candidate}

            doc = db.collection("courses").document(target_id).get()
            if doc.exists:
                return {"course_data": doc.to_dict()}
            else:
                return {
                    "error": f"Course document {target_id} not found.",
                    "tried_target_ids": candidates
                }
                
        elif query_type == "user_schedule":
            doc = db.collection("users").document(uid).get()
            if doc.exists:
                data = doc.to_dict() or {}
                # Return the timetables specifically so the agent can see active timetables and their courses
                timetables = _cap_timetables(data.get("timetables", []))
                active_id = data.get("activeTimetableId")
                return {
                    "timetables": timetables,
                    "activeTimetableId": active_id,
                    "program": data.get("program"),
                    "major": data.get("major"),
                    "majorName": data.get("majorName"),
                    "faculty": data.get("faculty"),
                    "yearLevel": data.get("yearLevel"),
                    "yearLevelLabel": data.get("yearLevelLabel")
                }
            return {"timetables": [], "message": "User document not found or no schedule saved."}
            
        elif query_type == "user_assistant":
            if not target_id:
                # Without term, retrieve all terms they generated for
                docs = db.collection("users").document(uid).collection("assistant").stream()
                return {"assistant_data": {d.id: d.to_dict() for d in docs}}
            
            doc = db.collection("users").document(uid).collection("assistant").document(target_id).get()
            if doc.exists:
                return {"assistant_data": doc.to_dict()}
            return {"message": "No assistant data found. The collection for this term is currently empty.", "assistant_data": {}}

        elif query_type == "major_graduation_requirement":
            user_doc = db.collection("users").document(uid).get()
            user_data = user_doc.to_dict() or {} if user_doc.exists else {}

            # Always prioritize the user's saved major to avoid invalid program IDs
            # being passed by model-generated tool arguments.
            user_major = str(user_data.get("major") or "").strip()
            user_major_name = str(user_data.get("majorName") or "").strip()
            requested = user_major or user_major_name or str(target_id or "").strip()
            if not requested:
                return {
                    "message": "No major specified. Provide target_id or save your major in profile first.",
                    "major_graduation_requirement": {}
                }

            collection = db.collection("major_graduation_requirement")
            candidates = [requested]
            normalized = requested.lower().replace("_", " ").strip()
            if normalized != requested:
                candidates.append(normalized)

            # 1) Direct doc-id lookup attempts.
            for candidate in candidates:
                doc = collection.document(candidate).get()
                if doc.exists:
                    return {
                        "major_graduation_requirement": doc.to_dict(),
                        "resolved_target_id": doc.id
                    }

            # 2) Fallback scan against common name fields.
            all_docs = collection.stream()
            for d in all_docs:
                payload = d.to_dict() or {}
                major_field = str(payload.get("major", "")).strip().lower()
                major_name_field = str(payload.get("majorName", "")).strip().lower()
                doc_id_field = d.id.strip().lower()
                if normalized in {major_field, major_name_field, doc_id_field}:
                    return {
                        "major_graduation_requirement": payload,
                        "resolved_target_id": d.id
                    }

            return {
                "message": f"No major graduation requirement found for '{requested}'.",
                "major_graduation_requirement": {}
            }
            
        else:
            return {"error": f"Unknown query_type: {query_type}"}

    except Exception as e:
        return {"error": str(e)}

def create_timetable(uid: str, title: str, term: str) -> dict:
    """
    Creates a new timetable for the user.
    """
    import uuid
    logger.info("Creating timetable uid=%s term=%s", _safe_uid(uid), term)
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}

    try:
        db = firestore.client()
        user_ref = db.collection("users").document(uid)
        new_id = str(uuid.uuid4())

        @_transactional
        def _txn(transaction):
            snap = user_ref.get(transaction=transaction)
            data = snap.to_dict() or {} if snap.exists else {}
            timetables = _cap_timetables(data.get("timetables", []))
            if len(timetables) >= MAX_TIMETABLES_PER_USER:
                raise _UserFacingError(
                    f"Timetable limit reached. You can keep up to {MAX_TIMETABLES_PER_USER} timetables."
                )
            timetables.append({"id": new_id, "name": title, "term": term, "courses": []})
            transaction.set(user_ref, {"timetables": timetables, "activeTimetableId": new_id}, merge=True)

        _txn(db.transaction())
        return {
            "message": f"Successfully created new timetable '{title}' for term {term}.",
            "timetable_id": new_id,
        }
    except _UserFacingError as e:
        return {"error": str(e)}
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

    def to_minutes(hour_val, minute_val):
        return int(hour_val) * 60 + int(minute_val)

    def has_time_conflict(existing_course, candidate_course):
        existing_days = set(existing_course.get("days", []) or [])
        candidate_days = set(candidate_course.get("days", []) or [])
        if not existing_days or not candidate_days:
            return False

        overlap_days = existing_days.intersection(candidate_days)
        if not overlap_days:
            return False

        existing_start = to_minutes(existing_course.get("startHour", 0), existing_course.get("startMinute", 0))
        existing_end = to_minutes(existing_course.get("endHour", 0), existing_course.get("endMinute", 0))
        candidate_start = to_minutes(candidate_course.get("startHour", 0), candidate_course.get("startMinute", 0))
        candidate_end = to_minutes(candidate_course.get("endHour", 0), candidate_course.get("endMinute", 0))

        if existing_end <= existing_start or candidate_end <= candidate_start:
            return False

        return candidate_start < existing_end and existing_start < candidate_end

    logger.info("Adding course to timetable uid=%s term=%s course=%s sections=%s", _safe_uid(uid), term, course_code, len(sections or []))
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
        section_map = {s["component"]: s for s in available_sections}

        # Validate all requested sections before entering the transaction.
        for sec_req in sections:
            if sec_req not in section_map:
                return {"error": f"Section component '{sec_req}' not found for course {course_code}. Available components: {list(section_map.keys())}"}

        # Pre-build course entries from global course data (no user state needed yet).
        new_course_entries = {}
        for sec_req in sections:
            sec_data = section_map[sec_req]
            t_info = parse_time_date(sec_data.get("time_date", ""))
            new_course_entries[sec_req] = {
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
                "location": str(sec_data.get("location", "")),
            }

        user_ref = db.collection("users").document(uid)

        @_transactional
        def _txn(transaction):
            import uuid as _uuid
            doc = user_ref.get(transaction=transaction)
            data = doc.to_dict() or {} if doc.exists else {}
            timetables = _cap_timetables(data.get("timetables", []))
            active_id = data.get("activeTimetableId")

            target_idx = _resolve_active_timetable_index(timetables, active_id)
            new_active_id = None
            if target_idx == -1:
                new_active_id = str(_uuid.uuid4())
                timetables.append({"id": new_active_id, "name": "My Timetable", "term": term, "courses": []})
                target_idx = 0

            scheduled_courses = list(timetables[target_idx].get("courses", []))
            if len(scheduled_courses) + len(sections) > MAX_COURSES_PER_TIMETABLE:
                raise _UserFacingError(
                    f"Course limit reached. A timetable can contain up to {MAX_COURSES_PER_TIMETABLE} sections."
                )

            added_components = []
            for sec_req in sections:
                new_course = new_course_entries[sec_req]
                prefix = sec_req.split(" ")[0] if " " in sec_req else sec_req
                filtered = [
                    c for c in scheduled_courses
                    if not (c.get("code") == course_code and c.get("term") == term
                            and c.get("component", "").startswith(prefix))
                ]
                for existing in filtered:
                    if str(existing.get("term", "")) != str(term):
                        continue
                    if has_time_conflict(existing, new_course):
                        conflict_time = (
                            f"{existing.get('startHour', 0):02d}:{existing.get('startMinute', 0):02d}-"
                            f"{existing.get('endHour', 0):02d}:{existing.get('endMinute', 0):02d}"
                        )
                        raise _UserFacingError(
                            f"Time conflict detected when adding {course_code} {sec_req}. "
                            f"Conflicts with {existing.get('code', 'Unknown')} {existing.get('component', '')} "
                            f"on {existing.get('days', [])} at {conflict_time}."
                        )
                filtered.append(new_course)
                scheduled_courses = filtered
                added_components.append(sec_req)

            timetables[target_idx]["courses"] = scheduled_courses
            update = {"timetables": timetables}
            if new_active_id:
                update["activeTimetableId"] = new_active_id
            transaction.set(user_ref, update, merge=True)
            return added_components

        added = _txn(db.transaction())
        return {"message": f"Successfully added/updated sections: {', '.join(added)} for {course_code} in term {term}."}
    except _UserFacingError as e:
        return {"error": str(e)}
    except Exception as e:
        return {"error": str(e)}

def delete_course_from_timetable(uid: str, term: str, course_code: str) -> dict:
    """
    Deletes a specific course from the user's timetable.
    """
    logger.info("Deleting course from timetable uid=%s term=%s course=%s", _safe_uid(uid), term, course_code)
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}

    try:
        db = firestore.client()
        user_ref = db.collection("users").document(uid)

        @_transactional
        def _txn(transaction):
            doc = user_ref.get(transaction=transaction)
            if not doc.exists:
                return "not_found"
            data = doc.to_dict() or {}
            timetables = _cap_timetables(data.get("timetables", []))
            target_idx = _resolve_active_timetable_index(timetables, data.get("activeTimetableId"))
            if target_idx == -1:
                return "no_timetable"
            scheduled_courses = timetables[target_idx].get("courses", [])
            filtered = [
                c for c in scheduled_courses
                if not (str(c.get("term")) == str(term) and c.get("code") == course_code)
            ]
            if len(filtered) == len(scheduled_courses):
                return "not_present"
            timetables[target_idx]["courses"] = filtered
            transaction.set(user_ref, {"timetables": timetables}, merge=True)
            return "ok"

        status = _txn(db.transaction())
        if status == "not_found":
            return {"message": "User document not found."}
        if status == "no_timetable":
            return {"message": "You don't have any existing timetables."}
        if status == "not_present":
            return {"message": f"Course {course_code} for term {term} is not present in the timetable."}
        return {"message": f"Successfully deleted {course_code} from timetable for term {term}."}
    except Exception as e:
        return {"error": str(e)}

def clear_timetable(uid: str, term: str) -> dict:
    """
    Clears the user's timetable for a specific term.
    """
    logger.info("Clearing timetable uid=%s term=%s", _safe_uid(uid), term)
    if not firebase_admin._apps:
        return {"error": "Firebase Admin SDK is not initialized."}

    try:
        db = firestore.client()
        user_ref = db.collection("users").document(uid)

        @_transactional
        def _txn(transaction):
            doc = user_ref.get(transaction=transaction)
            if not doc.exists:
                return "not_found"
            data = doc.to_dict() or {}
            timetables = _cap_timetables(data.get("timetables", []))
            target_idx = _resolve_active_timetable_index(timetables, data.get("activeTimetableId"))
            if target_idx == -1:
                return "no_timetable"
            timetables[target_idx]["courses"] = [
                c for c in timetables[target_idx].get("courses", [])
                if str(c.get("term")) != str(term)
            ]
            transaction.set(user_ref, {"timetables": timetables}, merge=True)
            return "ok"

        status = _txn(db.transaction())
        if status == "not_found":
            return {"message": "User document not found."}
        if status == "no_timetable":
            return {"message": "You don't have any existing timetables."}
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
            "name": "browse_uwflow",
            "description": "Searches UW Flow specifically for course and professor reviews, ratings, and related pages.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Course/professor search phrase, such as 'CS 341 reviews' or 'Trevor Brown UW Flow'"
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
            "description": "Accesses the timetable/profile database to retrieve schedules, major graduation requirements, assistant history, or specific course info.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query_type": {
                        "type": "string",
                        "enum": ["course_info", "user_schedule", "user_assistant", "major_graduation_requirement"],
                        "description": "Determine what to fetch. 'course_info': global courses. 'user_schedule': user's schedule + profile summary (program/major/year). 'user_assistant': generated timetables/wishlists. 'major_graduation_requirement': requirements doc for a major."
                    },
                    "target_id": {
                        "type": "string",
                        "description": "The specific document ID. Required for 'course_info' (e.g. '1255_ACTSC_221'). For 'user_assistant', it is the term code (e.g., '1255'). For 'major_graduation_requirement', pass major slug/name (optional if user profile already has a major). Optional/ignored for 'user_schedule'."
                    }
                },
                "required": ["query_type"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "create_timetable",
            "description": "Creates a new empty timetable for a specific term.",
            "parameters": {
                "type": "object",
                "properties": {
                    "title": { "type": "string", "description": "The title of the timetable (e.g., 'My Core Schedule')." },
                    "term": { "type": "string", "description": "The term for this timetable (e.g., 'Winter 2026' or '1261')." }
                },
                "required": ["title", "term"]
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
