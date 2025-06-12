import os
import sys
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from vector_store.utils import search_vector_store
from knowledge_graph.neo4j_query import query_graph
from llm_cypher_generator import generate_cypher_from_question

def ask_bot(user_question: str):
    lowered = user_question.lower()
    graph_keywords = ["status", "assigned", "project", "ticket", "label", "how many", "who", "belongs to"]

    if any(keyword in lowered for keyword in graph_keywords):
        cypher = generate_cypher_from_question(user_question)

        # Fallback if generated Cypher looks invalid
        if not any(cypher.strip().lower().startswith(kw) for kw in ["match", "with", "call", "return", "create"]):
            return {
                "result": search_vector_store(user_question),
                "debug": "Fallback to Vector DB due to invalid Cypher generated."
            }

        try:
            result = query_graph(cypher)
            if result:
                return {
                    "result": result,
                    "debug": f"Graph-based query used.\nCypher:\n{cypher}"
                }
            else:
                return {
                    "result": search_vector_store(user_question),
                    "debug": f"Graph result empty. Fallback to Vector DB.\nCypher was:\n{cypher}"
                }
        except Exception as e:
            return {
                "result": search_vector_store(user_question),
                "debug": f"Graph query failed. Fallback to Vector DB.\nError: {e}\nCypher: {cypher}"
            }

    # Default route → Vector DB
    return {
        "result": search_vector_store(user_question),
        "debug": "Vector DB used (semantic search)."
    }
