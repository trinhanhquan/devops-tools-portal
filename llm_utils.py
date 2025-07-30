# llm_utils.py

import pandas as pd

# Option A: OpenAI
# import openai

# Option B: Ollama via LangChain
from langchain.llms import Ollama

# Uncomment one of the below:
# openai.api_key = "your-api-key"  # If using OpenAI

llm = Ollama(model="llama3")  # If using Ollama

def ask_llm_for_chart_code(df: pd.DataFrame, question: str) -> str:
    """
    Ask LLM to generate matplotlib chart code from user's question and DataFrame.

    Args:
        df (pd.DataFrame): Sample data
        question (str): User's natural language question

    Returns:
        str: Python chart code as string
    """
    preview = df.head(5).to_markdown(index=False)

    prompt = f"""
You are a data visualization assistant.

The user has this data (pandas DataFrame named `df`):
{preview}

The user asks: "{question}"

Generate a Python code snippet that uses pandas and matplotlib to visualize a meaningful chart.

- Use `df` as the variable name.
- Do NOT include explanations or markdown.
- Just return Python code that defines and renders the chart.
"""

    response = llm.invoke(prompt)
    return response.strip()

