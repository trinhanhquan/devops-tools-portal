import re

def clean_chart_code(chart_code: str) -> str:
    chart_code = chart_code.strip()

    # Remove markdown and explanations
    chart_code = re.sub(r"^```(?:python)?", "", chart_code, flags=re.MULTILINE)
    chart_code = re.sub(r"```$", "", chart_code, flags=re.MULTILINE)

    # Remove junk explanation lines
    lines = chart_code.splitlines()
    lines = [
        line for line in lines
        if not line.strip().lower().startswith("data:")
        and "code snippet" not in line.lower()
    ]

    # Replace smart quotes
    chart_code = "\n".join(lines)
    chart_code = chart_code.replace("‘", "'").replace("’", "'")
    chart_code = chart_code.replace("“", '"').replace("”", '"')

    return chart_code.strip()

