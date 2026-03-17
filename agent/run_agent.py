import os
import json
from agent import Agent # Adjust import path as needed
from tools import read_uploaded_file, browse_online, query_database_readonly, TOOLS_SCHEMA

def main():
    """
    This entry point is for multi-round interaction and handle tools calls.
    The agent will call the tools as needed during the conversation.
    The messages list use OpenAI's format with multimodal support.  
    """
    # 1. Initialize the agent
    agent = Agent()
    
    # Load system prompt
    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys_prompt_path = os.path.join(current_dir, "system_prompt.txt")
    system_prompt = "You are a helpful academic assistant."
    if os.path.exists(sys_prompt_path):
        with open(sys_prompt_path, "r", encoding="utf-8") as f:
            system_prompt = f.read()

    # 2. Main conversation loop
    messages = [{"role": "system", "content": system_prompt}]
    print("Welcome to your Graphical Time Planner Agent. Type 'exit' to quit.")
    
    while True:
        # Get user input
        query = input("\nAsk the agent: ")
        if query.lower() in ["exit", "quit"]:
            print("Exiting the agent. Goodbye!")
            break
            
        file_path = input("Attach file path (or press Enter to skip): ").strip()
        attached_content = None
        if file_path:
            attached_content = read_uploaded_file(file_path)
            
        messages.append({"role": "user", "content": query})

        # Run continuous loop to handle tool calls
        while True:
            response_msg = agent.chat(messages, attached_file_content=attached_content, tools=TOOLS_SCHEMA)
            attached_content = None # Only attach once per user query
            
            # Print text content if any
            if response_msg.get("content"):
                print(f"\nAgent: {response_msg['content']}")
                
            messages.append(response_msg)
            
            # Check if there are tool calls
            tool_calls = response_msg.get("tool_calls")
            if not tool_calls:
                # No more tools, stop the loop and wait for next user input
                break
                
            # Process tool calls
            for t_call in tool_calls:
                func_name = t_call["function"]["name"]
                args = json.loads(t_call["function"]["arguments"])
                print(f"[System] Calling tool: {func_name} with args: {args}")
                
                # Default empty response
                tool_response = "Error: Tool execution failed or not found."
                
                try:
                    if func_name == "browse_online":
                        tool_response = browse_online(**args)
                    elif func_name == "query_database_readonly":
                        tool_response = query_database_readonly(**args)
                    else:
                        tool_response = f"Warning: Function {func_name} not recognized."
                except Exception as e:
                    tool_response = f"Error executing {func_name}: {e}"
                
                # Format to string if it returned a dict
                if isinstance(tool_response, dict):
                    tool_response = json.dumps(tool_response, ensure_ascii=False)
                
                # Append tool response
                messages.append({
                    "role": "tool",
                    "tool_call_id": t_call["id"],
                    "name": func_name,
                    "content": tool_response
                })

if __name__ == "__main__":
    main()

        