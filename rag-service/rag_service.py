from fastapi import FastAPI
from pydantic import BaseModel
import faiss
import numpy as np
from sentence_transformers import SentenceTransformer

app = FastAPI()

# Load embedding model (can switch to smaller/faster one)
model = SentenceTransformer("all-MiniLM-L6-v2")

# Simple FAISS index (cosine similarity)
dimension = 384  # depends on embedding model
index = faiss.IndexFlatIP(dimension)
documents = []  # store original chunks alongside embeddings

class InsertRequest(BaseModel):
    text: str

class QueryRequest(BaseModel):
    query: str
    k: int = 3

@app.post("/insert")
def insert(req: InsertRequest):
    emb = model.encode([req.text], normalize_embeddings=True)
    index.add(np.array(emb).astype("float32"))
    documents.append(req.text)
    return {"status": "ok", "count": len(documents)}

@app.post("/query")
def query(req: QueryRequest):
    emb = model.encode([req.query], normalize_embeddings=True)
    D, I = index.search(np.array(emb).astype("float32"), req.k)
    results = [documents[i] for i in I[0] if i < len(documents)]
    return {"results": results}