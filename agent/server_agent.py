from flask import Flask, request, jsonify
import os
import json
import base64
import tempfile
import uuid
from agent import Agent
from tools import read_uploaded_file, browse_online, query_database_readonly, add_course_to_timetable, delete_course_from_timetable, clear_timetable, show_timetable_button, TOOLS_SCHEMA

app = Flask(__name__)

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

    agent = Agent()

    # Load system prompt
    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys_prompt_path = os.path.join(current_dir, "system_prompt.txt")
    system_prompt = "You are a helpful academic assistant."
    if os.path.exists(sys_prompt_path):
        with open(sys_prompt_path, "r", encoding="utf-8") as f:
            system_prompt = f.read()

    # System prompt MUST be the first message
    messages = [{"role": "system", "content": system_prompt}]
    
    # Append the history
    for msg in history:
        messages.append({"role": msg.get("role"), "content": msg.get("content")})

    messages.append({"role": "user", "content": user_message})

    # Prepare attached file if any
    attached_content = None
    if file_bytes_base64:
        try:
            file_data = base64.b64decode(file_bytes_base64)
            # Find extension from file_name or default to txt
            ext = os.path.splitext(file_name)[1] if file_name else ".txt"
            # Write to a temp file
            temp_path = os.path.join(tempfile.gettempdir(), f"upload_{uuid.uuid4()}{ext}")
            with open(temp_path, "wb") as f:
                f.write(file_data)
            attached_content = read_uploaded_file(temp_path)
            os.remove(temp_path) # Cleanup
        except Exception as e:
            attached_content = f"Error reading attached file: {e}"

    # Loop to handle tool calls
    show_button = False
    while True:
        response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
        attached_content = None # Only attach once
        
        messages.append(response_msg)
        
        tool_calls = response_msg.get("tool_calls")
        if not tool_calls:
            # We got a final text response
            break
            
        for t_call in tool_calls:
            func_name = t_call["function"]["name"]
            try:
                args = json.loads(t_call["function"]["arguments"])
            except:
                args = {}
            
            # **Implicitly pass UID correctly to functions requiring it**
            if func_name in ["query_database_readonly", "add_course_to_timetable", "delete_course_from_timetable", "clear_timetable"]:
                args["uid"] = uid
            
            tool_response = "Error: Tool execution failed"
            try:
                if func_name == "browse_online":
                    tool_response = browse_online(**args)
                elif func_name == "query_database_readonly":
                    tool_response = query_database_readonly(**args)
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
            
            if isinstance(tool_response, dict):
                tool_response = json.dumps(tool_response, ensure_ascii=False)
                
            messages.append({
                "role": "tool",
                "tool_call_id": t_call["id"],
                "name": func_name,
                "content": str(tool_response)
            })
            
    # Extract just the last reply from assistant
    # (Since there could be multiple assistant responses if tools were called in between, just return the most recent one).
    final_response = response_msg.get("content", "")
    return jsonify({"response": final_response, "history": messages, "show_button": show_button})

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
