from flask import Flask, request, jsonify, Response, stream_with_context
import os
import json
import base64
import tempfile
import uuid
import re
import time
from collections import defaultdict, deque
from agent import Agent
from tools import read_uploaded_file, browse_online, browse_uwflow, query_database_readonly, create_timetable, add_course_to_timetable, delete_course_from_timetable, clear_timetable, show_timetable_button, TOOLS_SCHEMA
from firebase_admin import auth, firestore
from typing import Optional, Tuple

app = Flask(__name__)

MUTATION_TOOLS = {"create_timetable", "add_course_to_timetable", "delete_course_from_timetable", "clear_timetable"}
_SYSTEM_PROMPT_CACHE = None


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        raise ValueError(f"{name} must be an integer, got {raw!r}")


RATE_LIMIT_WINDOW_SECS = _env_int("AGENT_RATE_LIMIT_WINDOW_SECS", 60)
RATE_LIMIT_MAX_REQUESTS = _env_int("AGENT_RATE_LIMIT_MAX_REQUESTS", 20)
DAILY_QUOTA_MAX_COST = _env_int("AGENT_DAILY_QUOTA_MAX_COST", 200)
MAX_TOOL_CALLS_PER_REQUEST = _env_int("AGENT_MAX_TOOL_CALLS_PER_REQUEST", 12)

# Cost units are deliberately abstract. Tune them in Railway without code changes
# as provider pricing, model choice, and product limits evolve.
AGENT_USAGE_COSTS = {
    "summarize": _env_int("AGENT_COST_SUMMARIZE", 1),
    "generate_email": _env_int("AGENT_COST_GENERATE_EMAIL", 2),
    "chat": _env_int("AGENT_COST_CHAT", 5),
    "chat_stream": _env_int("AGENT_COST_CHAT_STREAM", 5),
}

_rate_limit_hits = defaultdict(deque)
_daily_usage = {}


def _json_error(message: str, status_code: int):
    return jsonify({"error": message}), status_code


def _require_uid():
    auth_header = request.headers.get("Authorization", "")
    prefix = "Bearer "
    if not auth_header.startswith(prefix):
        return None, _json_error("Missing Authorization bearer token.", 401)

    token = auth_header[len(prefix):].strip()
    if not token:
        return None, _json_error("Missing Authorization bearer token.", 401)

    try:
        decoded = auth.verify_id_token(token)
    except Exception:
        return None, _json_error("Invalid or expired sign-in token.", 401)

    uid = decoded.get("uid")
    if not uid:
        return None, _json_error("Invalid sign-in token.", 401)
    return uid, None


def _usage_day() -> str:
    return time.strftime("%Y-%m-%d", time.gmtime())


def _check_usage(uid: str, service: str):
    cost = AGENT_USAGE_COSTS[service]
    now = time.time()
    hits = _rate_limit_hits[uid]
    while hits and now - hits[0] > RATE_LIMIT_WINDOW_SECS:
        hits.popleft()

    if len(hits) >= RATE_LIMIT_MAX_REQUESTS:
        return False, _json_error("Too many requests. Please wait a moment and try again.", 429)
    hits.append(now)

    day = _usage_day()
    usage = _daily_usage.get(uid)
    if usage is None or usage["day"] != day:
        usage = {"day": day, "cost": 0}
        _daily_usage[uid] = usage

    if usage["cost"] + cost > DAILY_QUOTA_MAX_COST:
        return False, _json_error("Daily AI usage limit reached. Please try again tomorrow.", 429)

    usage["cost"] += cost
    return True, None


def _check_tool_budget(tool_count: int) -> Optional[dict]:
    if tool_count <= MAX_TOOL_CALLS_PER_REQUEST:
        return None
    return {
        "error": (
            "This request needs too many tool actions. Please narrow the request "
            "or split it into smaller steps."
        )
    }


def _normalize_advisor_response(text: str) -> str:
    """Normalize stylistic artifacts so chat output stays clean and consistent."""
    if not text:
        return ""

    cleaned = str(text)

    # Prefer ASCII arrows for readability in the app UI.
    cleaned = re.sub(r"[\u2192\u21d2\u27f6\u279d]", "->", cleaned)

    normalized_lines = []
    for line in cleaned.splitlines():
        # Convert bullets like: - *Some sentence* (Italian)  ->  - Some sentence
        m = re.match(r"^(\s*[-*]\s+)\*(.+?)\*\s*(\([A-Za-z][A-Za-z\s-]{1,24}\))?\s*$", line)
        if m:
            prefix, content, _ = m.groups()
            normalized_lines.append(f"{prefix}{content.strip()}")
            continue

        # Convert standalone italicized full-sentence lines to plain text.
        m2 = re.match(r"^(\s*)\*(.+?)\*\s*(\([A-Za-z][A-Za-z\s-]{1,24}\))?\s*$", line)
        if m2:
            indent, content, _ = m2.groups()
            normalized_lines.append(f"{indent}{content.strip()}")
            continue

        normalized_lines.append(line)

    return "\n".join(normalized_lines)

def _is_tool_error_response(tool_response) -> bool:
    if isinstance(tool_response, dict):
        return bool(tool_response.get("error"))
    text = str(tool_response).strip().lower()
    return text.startswith("error")


def _build_base_messages(system_prompt: str, history: list, user_message: str) -> list:
    messages = [{"role": "system", "content": system_prompt}]
    for msg in history:
        messages.append({"role": msg.get("role"), "content": msg.get("content")})
    messages.append({"role": "user", "content": user_message})
    return messages


def _load_system_prompt() -> str:
    global _SYSTEM_PROMPT_CACHE
    if _SYSTEM_PROMPT_CACHE is not None:
        return _SYSTEM_PROMPT_CACHE

    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys_prompt_path = os.path.join(current_dir, "system_prompt.txt")
    system_prompt = "You are a helpful academic assistant."
    if os.path.exists(sys_prompt_path):
        with open(sys_prompt_path, "r", encoding="utf-8") as f:
            system_prompt = f.read()
    _SYSTEM_PROMPT_CACHE = system_prompt
    return system_prompt


def _prepare_attached_content(file_name: str, file_bytes_base64: Optional[str]) -> Optional[str]:
    if not file_bytes_base64:
        return None
    try:
        file_data = base64.b64decode(file_bytes_base64)
        ext = os.path.splitext(file_name)[1] if file_name else ".txt"
        temp_path = os.path.join(tempfile.gettempdir(), f"upload_{uuid.uuid4()}{ext}")
        with open(temp_path, "wb") as f:
            f.write(file_data)
        attached_content = read_uploaded_file(temp_path)
        os.remove(temp_path)
        return attached_content
    except Exception as e:
        return f"Error reading attached file: {e}"


def _parse_course_code(course_code: str) -> Optional[Tuple[str, str]]:
    parts = (course_code or "").strip().split()
    if len(parts) < 2:
        return None
    subject = parts[0].upper()
    catalog = " ".join(parts[1:]).upper()
    return subject, catalog


def _lookup_course_title(term: str, course_code: str) -> str:
    parsed = _parse_course_code(course_code)
    if not parsed:
        return "Unknown course"
    subject, catalog = parsed
    try:
        db = firestore.client()
        doc_id = f"{term}_{subject}_{catalog}"
        doc = db.collection("courses").document(doc_id).get()
        if doc.exists:
            title = (doc.to_dict() or {}).get("title", "")
            return title if str(title).strip() else "Unknown course"
    except Exception:
        pass
    return "Unknown course"


def _lookup_active_timetable_name(uid: str) -> str:
    try:
        db = firestore.client()
        user_doc = db.collection("users").document(uid).get()
        if not user_doc.exists:
            return "Current timetable"
        data = user_doc.to_dict() or {}
        active_id = data.get("activeTimetableId")
        timetables = data.get("timetables", [])
        for t in timetables:
            if t.get("id") == active_id:
                name = t.get("name", "")
                if str(name).strip():
                    return str(name)
        if timetables:
            fallback = timetables[-1].get("name", "")
            if str(fallback).strip():
                return str(fallback)
    except Exception:
        pass
    return "Current timetable"


def _format_term_label(term: str) -> str:
    raw = str(term or "").strip()
    if not raw:
        return "Unknown term"

    # Supports common Waterloo-like numeric term formats such as 1261/1255/1259.
    match = re.fullmatch(r"1?(\d{2})([159])", raw)
    if not match:
        return raw

    yy = int(match.group(1))
    season_code = match.group(2)
    season = {
        "1": "Winter",
        "5": "Spring",
        "9": "Fall"
    }.get(season_code)
    if not season:
        return raw
    return f"{season} {2000 + yy}"


def _tool_progress_message(uid: str, func_name: str, args: dict) -> str:
    if func_name == "query_database_readonly":
        return "Querying database..."
    if func_name == "add_course_to_timetable":
        course_code = args.get("course_code", "Unknown course")
        term = args.get("term", "")
        course_name = _lookup_course_title(term, course_code)
        timetable_name = _lookup_active_timetable_name(uid)
        return f"Adding \"{course_code}: {course_name}\" to timetable \"{timetable_name}\""
    if func_name == "delete_course_from_timetable":
        course_code = args.get("course_code", "Unknown course")
        term = args.get("term", "")
        course_name = _lookup_course_title(term, course_code)
        timetable_name = _lookup_active_timetable_name(uid)
        return f"Removing \"{course_code}: {course_name}\" from timetable \"{timetable_name}\""
    if func_name == "browse_online":
        query = args.get("query", "")
        return f"Browsing online for \"{query}\""
    if func_name == "browse_uwflow":
        query = args.get("query", "")
        return f"Browsing UW Flow for \"{query}\""
    if func_name == "create_timetable":
        title = args.get("title", "New timetable")
        term = _format_term_label(args.get("term", "Unknown term"))
        return f"Creating timetable \"{title}\" for term \"{term}\""
    if func_name == "clear_timetable":
        term = _format_term_label(args.get("term", "Unknown term"))
        timetable_name = _lookup_active_timetable_name(uid)
        return f"Clearing timetable \"{timetable_name}\" for term \"{term}\""
    if func_name == "show_timetable_button":
        return "Preparing your new timetable..."
    return "Working on your request..."


def _run_chat(uid: str, user_message: str, history: list, file_name: str, file_bytes_base64: Optional[str], on_event=None):
    total_start = time.perf_counter()
    agent = Agent()

    system_prompt = _load_system_prompt()

    messages = _build_base_messages(system_prompt, history, user_message)
    attached_content = _prepare_attached_content(file_name, file_bytes_base64)

    show_button = False
    llm_round_ms = []
    tool_stats = []
    loop_count = 0

    while True:
        llm_start = time.perf_counter()
        response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
        llm_round_ms.append(round((time.perf_counter() - llm_start) * 1000, 1))
        loop_count += 1
        attached_content = None
        messages.append(response_msg)

        tool_calls = response_msg.get("tool_calls")
        if not tool_calls:
            break

        for t_call in tool_calls:
            budget_error = _check_tool_budget(len(tool_stats) + 1)
            if budget_error:
                return {
                    "response": budget_error["error"],
                    "history": messages,
                    "show_button": show_button,
                    "metrics": {
                        "total_ms": round((time.perf_counter() - total_start) * 1000, 1),
                        "llm_rounds": len(llm_round_ms),
                        "llm_round_ms": llm_round_ms,
                        "tool_calls": len(tool_stats),
                        "tool_stats": tool_stats,
                        "loop_count": loop_count,
                        "stopped_by_tool_budget": True
                    }
                }

            func_name = t_call["function"]["name"]
            try:
                args = json.loads(t_call["function"]["arguments"])
            except Exception:
                args = {}

            if on_event:
                on_event({
                    "type": "tool",
                    "tool_name": func_name,
                    "message": _tool_progress_message(uid, func_name, args)
                })

            if func_name in ["query_database_readonly", "create_timetable", "add_course_to_timetable", "delete_course_from_timetable", "clear_timetable"]:
                args["uid"] = uid

            tool_response = "Error: Tool execution failed"
            tool_start = time.perf_counter()
            try:
                if func_name == "browse_online":
                    tool_response = browse_online(**args)
                elif func_name == "browse_uwflow":
                    tool_response = browse_uwflow(**args)
                elif func_name == "query_database_readonly":
                    tool_response = query_database_readonly(**args)
                elif func_name == "create_timetable":
                    tool_response = create_timetable(**args)
                elif func_name == "add_course_to_timetable":
                    tool_response = add_course_to_timetable(**args)
                elif func_name == "delete_course_from_timetable":
                    tool_response = delete_course_from_timetable(**args)
                elif func_name == "clear_timetable":
                    tool_response = clear_timetable(**args)
                elif func_name == "show_timetable_button":
                    show_button = True
                    tool_response = show_timetable_button(**args)
                else:
                    tool_response = f"Warning: Function {func_name} not recognized."
            except Exception as e:
                tool_response = f"Error executing {func_name}: {e}"

            tool_elapsed_ms = round((time.perf_counter() - tool_start) * 1000, 1)
            tool_stats.append({
                "name": func_name,
                "duration_ms": tool_elapsed_ms,
                "ok": not _is_tool_error_response(tool_response)
            })

            print(f"Tool '{func_name}' response: {tool_response}")

            if isinstance(tool_response, dict):
                tool_response = json.dumps(tool_response, ensure_ascii=False)

            messages.append({
                "role": "tool",
                "tool_call_id": t_call["id"],
                "name": func_name,
                "content": str(tool_response)
            })

    final_response = _normalize_advisor_response(response_msg.get("content", ""))
    metrics = {
        "total_ms": round((time.perf_counter() - total_start) * 1000, 1),
        "llm_rounds": len(llm_round_ms),
        "llm_round_ms": llm_round_ms,
        "tool_calls": len(tool_stats),
        "tool_stats": tool_stats,
        "loop_count": loop_count
    }
    return {
        "response": final_response,
        "history": messages,
        "show_button": show_button,
        "metrics": metrics
    }


def _parse_email_json(content: str) -> Optional[dict]:
    text = (content or "").strip()
    if not text:
        return None

    # Strip fenced code blocks if model wrapped JSON in markdown.
    if text.startswith("```"):
        lines = text.splitlines()
        if len(lines) >= 3:
            text = "\n".join(lines[1:-1]).strip()

    try:
        data = json.loads(text)
        if isinstance(data, dict):
            return data
    except Exception:
        pass

    # Fallback: extract first JSON object from mixed text.
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            data = json.loads(text[start:end + 1])
            if isinstance(data, dict):
                return data
        except Exception:
            return None
    return None


@app.route('/generate_email', methods=['POST'])
def generate_email():
    uid, auth_error = _require_uid()
    if auth_error:
        return auth_error
    ok, usage_error = _check_usage(uid, service="generate_email")
    if not ok:
        return usage_error

    data = request.json or {}
    issue = (data.get("issue") or "").strip()
    advisor_name = (data.get("advisor_name") or "Academic Advisor").strip()
    program_name = (data.get("program_name") or "").strip()
    year_level = (data.get("year_level") or "").strip()
    student_name = (data.get("student_name") or "").strip()
    student_id = (data.get("student_id") or "").strip()

    if not issue:
        return jsonify({"error": "issue is required"}), 400

    total_start = time.perf_counter()
    agent = Agent()
    system_prompt = (
        "You are an academic-email drafting assistant. Write professional advisor emails that strictly preserve "
        "the user's intent and facts. You must not invent details, assumptions, timelines, course codes, policy claims, "
        "or personal information that are not explicitly provided. If information is missing, keep wording generic instead "
        "of guessing. Output valid JSON only with exactly two keys: subject and body."
    )

    context_lines = []
    if program_name:
        context_lines.append(f"Program: {program_name}")
    if year_level:
        context_lines.append(f"Year level: {year_level}")
    if student_name:
        context_lines.append(f"Student name: {student_name}")
    if student_id:
        context_lines.append(f"Student ID: {student_id}")
    context_block = "\n".join(context_lines) if context_lines else "No additional profile context provided."

    user_prompt = (
        f"Advisor name: {advisor_name}\n"
        f"{context_block}\n\n"
        f"Issue summary:\n{issue}\n\n"
        "Hard constraints:\n"
        "1) Keep semantic fidelity: reflect only what appears in Issue summary and provided context.\n"
        "2) Do not add any new facts (no fabricated courses, dates, deadlines, medical/personal claims, or prior conversations).\n"
        "3) If the issue is vague, do not infer specifics; request guidance in general terms.\n"
        "4) Keep tone polite, concise, and student-appropriate.\n"
        "5) Include a direct ask for advisor guidance or next steps.\n"
        "6) Subject must be specific to the issue and under 12 words.\n"
        "7) Body must be 90-220 words, with greeting, concise context, request, and professional closing.\n"
        "8) Preserve first-person student voice.\n"
        "9) Return JSON only.\n"
        "Example format: {\"subject\":\"...\",\"body\":\"...\"}"
    )

    try:
        llm_start = time.perf_counter()
        response_msg = agent.chat([
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ])
        llm_ms = round((time.perf_counter() - llm_start) * 1000, 1)
        content = response_msg.get("content", "")
        parsed = _parse_email_json(content)

        if parsed:
            subject = str(parsed.get("subject", "")).strip()
            body = str(parsed.get("body", "")).strip()
            if subject and body:
                return jsonify({
                    "subject": subject,
                    "body": body,
                    "metrics": {
                        "total_ms": round((time.perf_counter() - total_start) * 1000, 1),
                        "llm_ms": llm_ms
                    }
                }), 200

        # Fallback if model returns non-JSON or incomplete JSON
        fallback_body = (
            f"Dear {advisor_name},\n\n"
            "I hope you are doing well. I am writing to ask for your guidance regarding the following issue:\n\n"
            f"{issue}\n\n"
            "I would really appreciate your advice on possible next steps. "
            "Please let me know if you need any additional information from me.\n\n"
            "Thank you for your time and support.\n\n"
            "Best regards,\n"
            f"{student_name or '[Your Name]'}"
            + (f"\n{student_id}" if student_id else "\n[Student ID]")
        )
        return jsonify({
            "subject": "Request for Academic Advising Assistance",
            "body": fallback_body,
            "metrics": {
                "total_ms": round((time.perf_counter() - total_start) * 1000, 1),
                "llm_ms": llm_ms
            }
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/chat', methods=['POST'])
def chat():
    uid, auth_error = _require_uid()
    if auth_error:
        return auth_error
    ok, usage_error = _check_usage(uid, service="chat")
    if not ok:
        return usage_error

    data = request.json or {}
    user_message = data.get("message")
    history = data.get("history", [])
    file_name = data.get("file_name", "")
    file_bytes_base64 = data.get("file_bytes")  # Base64 string from Android if any

    if not user_message:
        return jsonify({"error": "message is required"}), 400

    result = _run_chat(uid, user_message, history, file_name, file_bytes_base64)
    return jsonify(result)


@app.route('/chat_stream', methods=['POST'])
def chat_stream():
    uid, auth_error = _require_uid()
    if auth_error:
        return auth_error
    ok, usage_error = _check_usage(uid, service="chat_stream")
    if not ok:
        return usage_error

    data = request.json or {}
    user_message = data.get("message")
    history = data.get("history", [])
    file_name = data.get("file_name", "")
    file_bytes_base64 = data.get("file_bytes")

    if not user_message:
        return jsonify({"error": "message is required"}), 400

    def generate():
        def emit(event: dict):
            payload = json.dumps(event, ensure_ascii=False)
            return f"{payload}\n"

        try:
            total_start = time.perf_counter()
            agent = Agent()
            system_prompt = _load_system_prompt()

            messages = _build_base_messages(system_prompt, history, user_message)
            attached_content = _prepare_attached_content(file_name, file_bytes_base64)

            show_button = False
            llm_round_ms = []
            tool_stats = []
            loop_count = 0

            while True:
                llm_start = time.perf_counter()
                response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
                llm_round_ms.append(round((time.perf_counter() - llm_start) * 1000, 1))
                loop_count += 1
                attached_content = None
                messages.append(response_msg)

                tool_calls = response_msg.get("tool_calls")
                if not tool_calls:
                    break

                for t_call in tool_calls:
                    budget_error = _check_tool_budget(len(tool_stats) + 1)
                    if budget_error:
                        yield emit({"type": "error", "message": budget_error["error"]})
                        return

                    func_name = t_call["function"]["name"]
                    try:
                        args = json.loads(t_call["function"]["arguments"])
                    except Exception:
                        args = {}

                    yield emit({
                        "type": "tool",
                        "tool_name": func_name,
                        "message": _tool_progress_message(uid, func_name, args)
                    })

                    if func_name in ["query_database_readonly", "create_timetable", "add_course_to_timetable", "delete_course_from_timetable", "clear_timetable"]:
                        args["uid"] = uid

                    tool_response = "Error: Tool execution failed"
                    tool_start = time.perf_counter()
                    try:
                        if func_name == "browse_online":
                            tool_response = browse_online(**args)
                        elif func_name == "browse_uwflow":
                            tool_response = browse_uwflow(**args)
                        elif func_name == "query_database_readonly":
                            tool_response = query_database_readonly(**args)
                        elif func_name == "create_timetable":
                            tool_response = create_timetable(**args)
                        elif func_name == "add_course_to_timetable":
                            tool_response = add_course_to_timetable(**args)
                        elif func_name == "delete_course_from_timetable":
                            tool_response = delete_course_from_timetable(**args)
                        elif func_name == "clear_timetable":
                            tool_response = clear_timetable(**args)
                        elif func_name == "show_timetable_button":
                            show_button = True
                            tool_response = show_timetable_button(**args)
                        else:
                            tool_response = f"Warning: Function {func_name} not recognized."
                    except Exception as e:
                        tool_response = f"Error executing {func_name}: {e}"

                    tool_elapsed_ms = round((time.perf_counter() - tool_start) * 1000, 1)
                    tool_stats.append({
                        "name": func_name,
                        "duration_ms": tool_elapsed_ms,
                        "ok": not _is_tool_error_response(tool_response)
                    })

                    print(f"Tool '{func_name}' response: {tool_response}")

                    if isinstance(tool_response, dict):
                        tool_response = json.dumps(tool_response, ensure_ascii=False)

                    messages.append({
                        "role": "tool",
                        "tool_call_id": t_call["id"],
                        "name": func_name,
                        "content": str(tool_response)
                    })

            final_response = _normalize_advisor_response(response_msg.get("content", ""))

            yield emit({
                "type": "final",
                "response": final_response,
                "show_button": show_button,
                "metrics": {
                    "total_ms": round((time.perf_counter() - total_start) * 1000, 1),
                    "llm_rounds": len(llm_round_ms),
                    "llm_round_ms": llm_round_ms,
                    "tool_calls": len(tool_stats),
                    "tool_stats": tool_stats,
                    "loop_count": loop_count
                }
            })
        except Exception as e:
            yield emit({"type": "error", "message": str(e)})

    return Response(stream_with_context(generate()), mimetype='application/x-ndjson')

@app.route('/summarize', methods=['POST'])
def summarize():
    uid, auth_error = _require_uid()
    if auth_error:
        return auth_error
    ok, usage_error = _check_usage(uid, service="summarize")
    if not ok:
        return usage_error

    data = request.json or {}
    user_message = data.get("message")
    
    if not user_message:
        return jsonify({"summary": "Chat"}), 200

    agent = Agent()
    system_prompt = "You are an AI that generates a concise, 2-6 word title for a user's initial prompt to start a chat session. Do not include quotes. Do not say 'Here is the title:'. Just provide the title."
    
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"Summarize this prompt into a short chat title: {user_message}"}
    ]
    
    response_msg = agent.chat(messages)
    title = response_msg.get("content", "Chat Session").strip('"\'\n\r \t')
    return jsonify({"summary": title}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
