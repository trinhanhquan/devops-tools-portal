import re

def clean_chart_code(chart_code: str) -> str:
    """
    Remove markdown, explanations, and invalid code lines from LLM output.
    """
    chart_code = chart_code.strip()

    # Remove markdown-style backticks
    chart_code = re.sub(r"^```(?:python)?", "", chart_code, flags=re.MULTILINE)
    chart_code = re.sub(r"```$", "", chart_code, flags=re.MULTILINE)

    # Remove non-Python comment lines like 'data: ...'
    chart_code = "\n".join(
        line for line in chart_code.splitlines()
        if not line.strip().lower().startswith("data:")
    )

    # Remove smart quotes (just in case)
    chart_code = chart_code.replace("‘", "'").replace("’", "'").replace("“", '"').replace("”", '"')

    return chart_code.strip()

