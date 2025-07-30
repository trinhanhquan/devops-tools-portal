import matplotlib.pyplot as plt
import pandas as pd
from io import BytesIO
import base64

def generate_chart(question: str):
    # Dummy example using hardcoded data
    data = {
        "To Do": 12,
        "In Progress": 7,
        "Done": 15
    }

    df = pd.DataFrame(list(data.items()), columns=["Status", "Count"])
    
    fig, ax = plt.subplots()
    df.set_index("Status").plot(kind="bar", legend=False, ax=ax)
    plt.title("Issue Status Distribution")
    plt.ylabel("Count")

    # Save to base64 string for Streamlit
    buf = BytesIO()
    plt.savefig(buf, format="png")
    buf.seek(0)
    image_base64 = base64.b64encode(buf.read()).decode()

    return image_base64, "Issue Status Distribution"

