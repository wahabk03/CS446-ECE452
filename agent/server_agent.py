from flask import Flask, request, jsonify, Response, stream_with_context
import os
import json
import base64
import tempfile
import uuid
import re
from agent import Agent
from tools import read_uploaded_file, browse_online, query_database_readonly, create_timetable, add_course_to_timetable, delete_course_from_timetable, clear_timetable, show_timetable_button, TOOLS_SCHEMA
from firebase_admin import firestore
from typing import Optional, Tuple

app = Flask(__name__)

MUTATION_TOOLS = {"create_timetable", "add_course_to_timetable", "delete_course_from_timetable", "clear_timetable"}

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
    agent = Agent()

    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys_prompt_path = os.path.join(current_dir, "system_prompt.txt")
    system_prompt = "You are a helpful academic assistant."
    if os.path.exists(sys_prompt_path):
        with open(sys_prompt_path, "r", encoding="utf-8") as f:
            system_prompt = f.read()

    messages = _build_base_messages(system_prompt, history, user_message)
    attached_content = _prepare_attached_content(file_name, file_bytes_base64)

    show_button = False
    had_mutation_success = False
    had_mutation_error = False

    while True:
        response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
        attached_content = None
        messages.append(response_msg)

        tool_calls = response_msg.get("tool_calls")
        if not tool_calls:
            break

        for t_call in tool_calls:
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
            try:
                if func_name == "browse_online":
                    tool_response = browse_online(**args)
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
                    if had_mutation_success and not had_mutation_error:
                        show_button = True
                        tool_response = show_timetable_button(**args)
                    else:
                        tool_response = {
                            "error": "Cannot show timetable button because no successful timetable update was confirmed in this request."
                        }
                else:
                    tool_response = f"Warning: Function {func_name} not recognized."
            except Exception as e:
                tool_response = f"Error executing {func_name}: {e}"

            if func_name in MUTATION_TOOLS:
                if _is_tool_error_response(tool_response):
                    had_mutation_error = True
                else:
                    had_mutation_success = True

            print(f"Tool '{func_name}' response: {tool_response}")

            if isinstance(tool_response, dict):
                tool_response = json.dumps(tool_response, ensure_ascii=False)

            messages.append({
                "role": "tool",
                "tool_call_id": t_call["id"],
                "name": func_name,
                "content": str(tool_response)
            })

    final_response = response_msg.get("content", "")
    return {
        "response": final_response,
        "history": messages,
        "show_button": show_button
    }

@app.route('/chat', methods=['POST'])
def chat():
    data = request.json
    uid = data.get("uid")
    user_message = data.get("message")
    history = data.get("history", [])
    file_name = data.get("file_name", "")
    file_bytes_base64 = data.get("file_bytes")  # Base64 string from Android if any

    if not uid or not user_message:
        return jsonify({"error": "uid and message are required"}), 400

    result = _run_chat(uid, user_message, history, file_name, file_bytes_base64)
    return jsonify(result)


@app.route('/chat_stream', methods=['POST'])
def chat_stream():
    data = request.json
    uid = data.get("uid")
    user_message = data.get("message")
    history = data.get("history", [])
    file_name = data.get("file_name", "")
    file_bytes_base64 = data.get("file_bytes")

    if not uid or not user_message:
        return jsonify({"error": "uid and message are required"}), 400

    def generate():
        def emit(event: dict):
            payload = json.dumps(event, ensure_ascii=False)
            return f"{payload}\n"

        try:
            agent = Agent()

            current_dir = os.path.dirname(os.path.abspath(__file__))
            sys_prompt_path = os.path.join(current_dir, "system_prompt.txt")
            system_prompt = "You are a helpful academic assistant."
            if os.path.exists(sys_prompt_path):
                with open(sys_prompt_path, "r", encoding="utf-8") as f:
                    system_prompt = f.read()

            messages = _build_base_messages(system_prompt, history, user_message)
            attached_content = _prepare_attached_content(file_name, file_bytes_base64)

            show_button = False
            had_mutation_success = False
            had_mutation_error = False

            while True:
                response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
                attached_content = None
                messages.append(response_msg)

                tool_calls = response_msg.get("tool_calls")
                if not tool_calls:
                    break

                for t_call in tool_calls:
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
                    try:
                        if func_name == "browse_online":
                            tool_response = browse_online(**args)
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
                            if had_mutation_success and not had_mutation_error:
                                show_button = True
                                tool_response = show_timetable_button(**args)
                            else:
                                tool_response = {
                                    "error": "Cannot show timetable button because no successful timetable update was confirmed in this request."
                                }
                        else:
                            tool_response = f"Warning: Function {func_name} not recognized."
                    except Exception as e:
                        tool_response = f"Error executing {func_name}: {e}"

                    if func_name in MUTATION_TOOLS:
                        if _is_tool_error_response(tool_response):
                            had_mutation_error = True
                        else:
                            had_mutation_success = True

                    print(f"Tool '{func_name}' response: {tool_response}")

                    if isinstance(tool_response, dict):
                        tool_response = json.dumps(tool_response, ensure_ascii=False)

                    messages.append({
                        "role": "tool",
                        "tool_call_id": t_call["id"],
                        "name": func_name,
                        "content": str(tool_response)
                    })

            final_response = response_msg.get("content", "")

            yield emit({
                "type": "final",
                "response": final_response,
                "show_button": show_button
            })
        except Exception as e:
            yield emit({"type": "error", "message": str(e)})

    return Response(stream_with_context(generate()), mimetype='application/x-ndjson')

@app.route('/summarize', methods=['POST'])
def summarize():
    data = request.json
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
