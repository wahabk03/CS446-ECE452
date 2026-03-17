import os
import requests
from llm_config import LLM_CONFIG
from llm_config import SERPAPI_API_KEY

class Agent:
    def __init__(self):
        self.api_key = LLM_CONFIG["config_list"][0]["api_key"]
        self.base_url = LLM_CONFIG["config_list"][0]["base_url"]
        self.model = LLM_CONFIG["config_list"][0]["model"]
        
        self.temperature = LLM_CONFIG["temperature"]
        self.max_tokens = LLM_CONFIG["max_tokens"]

    def chat(
        self, 
        messages: list, 
        attached_file_content: str = None,
        tools: list = None
    ) -> dict:
        """
        Main method to interact with the LLM.
        Returns the assistant's message dict.
        """
        prompt = self.construct_prompt(messages, attached_file_content)
        response_msg = self.call_llm_api(prompt, tools)
        return response_msg

    def construct_prompt(
        self, 
        messages: list, 
        attached_file_content: str = None
    ) -> list:
        """
        Constructs the list of message dicts required by the API.
        If file content is provided, prepends it to the last user message.
        """
        import copy
        # Create a deep copy so we don't modify the original messages list directly
        formatted_messages = copy.deepcopy(messages)
        
        # If there's an attached file, we inject it into the most recent user message
        if attached_file_content:
            for message in reversed(formatted_messages):
                if message.get("role") == "user":
                    original_content = message.get("content", "")
                    
                    if attached_file_content.startswith("data:application/pdf;base64,"):
                        # If the model natively supports base64 files
                        message["content"] = [
                            {"type": "text", "text": f"{original_content}"},
                            {"type": "file_url", "file_url": {"url": attached_file_content}}
                        ]
                    else:
                        # Otherwise append string explicitly
                        message["content"] = f"Here is the content of the attached file:\n\n{attached_file_content}\n\nUser's question:\n{original_content}"
                    break
            else:
                # If no user message was found but we have file content, append one
                formatted_messages.append({
                    "role": "user",
                    "content": f"Here is the content of the attached file:\n\n{attached_file_content}"
                })
                
        return formatted_messages

    def call_llm_api(self, formatted_messages: list, tools: list = None) -> dict:
        """
        Sends the formatted messages to the SiliconFlow API and returns the message dict.
        """
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "model": self.model,
            "messages": formatted_messages,
            "stream": False
        }
        
        if tools:
            payload["tools"] = tools

        # To avoid strict backend errors, provide safe ranges
        if self.temperature is not None:
            payload["temperature"] = max(0.01, min(0.99, self.temperature))
        if self.max_tokens is not None:
            payload["max_tokens"] = min(self.max_tokens, 4096)
            
        # SiliconFlow's vision/multimodal models often require content to be formatted
        # specifically if they are strictly expecting a VLM schema even for text.
        for msg in payload["messages"]:
            if isinstance(msg.get("content"), str) and "V" in self.model.upper():
                msg["content"] = [{"type": "text", "text": msg["content"]}]
        
        url = f"{self.base_url}/chat/completions"
        try:
            response = requests.post(url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]
        except Exception as e:
            print(f"Error calling LLM API: {e}")
            if 'response' in locals() and hasattr(response, 'text'):
                print(f"Response details: {response.text}")
            return {"role": "assistant", "content": "Sorry, I encountered an error connecting to the agent's brain."}