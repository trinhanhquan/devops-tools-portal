from langchain.llms import Ollama

def generate_answer_from_vector(user_question, documents, model="llama3", base_url="http://172.29.227.59:11434"):
    """
    Generate an LLM response based on retrieved vector documents.
    """
    llm = Ollama(model=model, base_url=base_url)

    context = "\n".join(doc.page_content for doc in documents)
    prompt = f"""Use the context below to answer the user's question.

Context:
{context}

Question: {user_question}

Answer:"""

    return llm.invoke(prompt)
