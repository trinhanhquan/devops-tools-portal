# chart_utils.py

import matplotlib.pyplot as plt
from io import BytesIO
import base64
import pandas as pd
from .llm_utils import ask_llm_for_chart_code


def run_code_and_get_base64(chart_code: str, df: pd.DataFrame) -> str:
    """
    Executes LLM-generated matplotlib code and returns chart as base64 image.
    """
    exec_namespace = {
        "df": df,
        "plt": plt
    }

    # Clear previous plots
    plt.clf()

    try:
        exec(chart_code, exec_namespace)

        buf = BytesIO()
        plt.savefig(buf, format="png", bbox_inches='tight')
        buf.seek(0)
        image_base64 = base64.b64encode(buf.read()).decode()

        return image_base64
    except Exception as e:
        print(f"[ERROR running chart code] {e}")
        return None


def generate_chart(question: str, docs) -> tuple[str, str]:
    """
    Generates chart based on FAISS docs + question.

    Args:
        question (str): Natural language question from user
        docs (list): List of FAISS Document objects (with .metadata)

    Returns:
        (base64_image: str, caption: str)
    """
    if not docs:
        return None, "No data found."

    data = [doc.metadata for doc in docs if isinstance(doc.metadata, dict)]
    df = pd.DataFrame(data)

    if df.empty:
        return None, "No structured metadata found."

    chart_code = ask_llm_for_chart_code(df, question)
    image_base64 = run_code_and_get_base64(chart_code, df)

    if image_base64:
        return image_base64, "Chart generated from FAISS metadata"
    else:
        return None, "Chart generation failed"

