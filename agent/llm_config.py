import os

# SiliconFlow API Configuration
SILICONFLOW_API_KEY = os.getenv("SILICONFLOW_API_KEY", "your_api_key_here")
SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1"

# SerpAPI Configuration
SERPAPI_API_KEY = os.getenv("SERPAPI_API_KEY", "your_serpapi_key_here")

# Model Configuration
MODEL_NAME = "Pro/MiniMaxAI/MiniMax-M2.5" # Need a text model with tool-calling capabilities
TEMPERATURE = 0.7
MAX_TOKENS = 4096

LLM_CONFIG = {
    "config_list": [
        {
            "model": MODEL_NAME,
            "api_key": SILICONFLOW_API_KEY,
            "base_url": SILICONFLOW_BASE_URL,
        }
    ],
    "temperature": TEMPERATURE,
    "max_tokens": MAX_TOKENS,
}