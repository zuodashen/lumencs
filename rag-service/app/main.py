from __future__ import annotations

import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.embeddings import EmbeddingClient
from app.store import QdrantStore

load_dotenv()

app = FastAPI(title="LumenCS RAG Service", version="1.0.0")

embedder = EmbeddingClient()
dim = int(os.getenv("EMBEDDING_DIM", "1536"))
store = QdrantStore(dim=dim)


class IngestPoint(BaseModel):
    id: str
    text: str
    payload: dict[str, Any] = Field(default_factory=dict)


class IngestRequest(BaseModel):
    points: list[IngestPoint]


class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    document_id: int | None = None


class DeleteRequest(BaseModel):
    document_id: int


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "healthy",
        "service": "rag",
        "dim": dim,
        "embedding_model": os.getenv("EMBEDDING_MODEL", "text-embedding-3-small"),
    }


@app.post("/ingest")
def ingest(req: IngestRequest) -> dict[str, Any]:
    if not req.points:
        return {"upserted": 0}
    try:
        vectors = embedder.embed([p.text for p in req.points])
        points = []
        for point, vector in zip(req.points, vectors):
            payload = dict(point.payload)
            payload.setdefault("content", point.text)
            points.append({"id": point.id, "vector": vector, "payload": payload})
        count = store.upsert(points)
        return {"upserted": count}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"ingest failed: {exc}") from exc


@app.post("/search")
def search(req: SearchRequest) -> dict[str, Any]:
    try:
        query_vec = embedder.embed([req.query])[0]
        hits = store.search(query_vec, top_k=max(1, req.top_k), document_id=req.document_id)
        return {"hits": hits}
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"search failed: {exc}") from exc


@app.post("/delete")
def delete_document(req: DeleteRequest) -> dict[str, str]:
    store.delete_by_document(req.document_id)
    return {"status": "ok"}
