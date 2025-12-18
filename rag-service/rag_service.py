
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional

from langchain_community.vectorstores import FAISS
from langchain_community.embeddings import HuggingFaceEmbeddings

import os

app = FastAPI()

# ---- Embeddings ----
EMBEDDING_MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")

embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL_NAME)

# ---- Global vector store (in-memory for now) ----
vector_store: Optional[FAISS] = None

class InsertRequest(BaseModel):
    text: str

class QueryRequest(BaseModel):
    query: str
    k: int = 3


@app.post("/insert")
def insert(req: InsertRequest):
    """
    Insert a single text chunk into the vector store.
    If this is the first insert, create the store with from_texts().
    Otherwise, call add_texts().
    """
    global vector_store

    try:
        if vector_store is None:
            # First time: create a new FAISS index from this text
            vector_store = FAISS.from_texts(
                texts=[req.text],
                embedding=embeddings
            )
        else:
            # Subsequent inserts: just add texts
            vector_store.add_texts([req.text])

        return {"status": "ok"}
    except Exception as e:
        print("Error in /insert:", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/query")
def query(req: QueryRequest):
    """
    Query the vector store for the top-k most similar chunks.
    Returns a JSON with {"results": [ ...chunks... ]} to match Java RetrievalService.
    """
    if vector_store is None:
        # No docs inserted yet
        return {"results": []}

    try:
        docs = vector_store.similarity_search(req.query, k=req.k)
        # docs is a list of Document(page_content=..., metadata=...)
        results: List[str] = [d.page_content for d in docs]
        return {"results": results}
    except Exception as e:
        print("Error in /query:", e)
        raise HTTPException(status_code=500, detail=str(e))