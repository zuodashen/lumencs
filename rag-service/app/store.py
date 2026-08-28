from __future__ import annotations

import os
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

COLLECTION = os.getenv("QDRANT_COLLECTION", "lumencs_chunks")


class QdrantStore:
    def __init__(self, dim: int) -> None:
        url = os.getenv("QDRANT_URL", "http://localhost:6333")
        self.client = QdrantClient(url=url, timeout=30)
        self.dim = dim
        last_error: Exception | None = None
        for _ in range(20):
            try:
                self._ensure_collection()
                last_error = None
                break
            except Exception as exc:  # qdrant may still be starting
                last_error = exc
                import time
                time.sleep(1.5)
        if last_error:
            raise last_error

    def _ensure_collection(self) -> None:
        existing = {c.name for c in self.client.get_collections().collections}
        if COLLECTION in existing:
            info = self.client.get_collection(COLLECTION)
            vectors = info.config.params.vectors
            current_dim = getattr(vectors, "size", None)
            if current_dim and current_dim != self.dim:
                self.client.delete_collection(COLLECTION)
                existing.discard(COLLECTION)
        if COLLECTION not in existing:
            self.client.create_collection(
                collection_name=COLLECTION,
                vectors_config=VectorParams(size=self.dim, distance=Distance.COSINE),
            )

    def upsert(self, points: list[dict[str, Any]]) -> int:
        payload = [
            PointStruct(
                id=item["id"],
                vector=item["vector"],
                payload=item.get("payload") or {},
            )
            for item in points
        ]
        if not payload:
            return 0
        self.client.upsert(collection_name=COLLECTION, points=payload)
        return len(payload)

    def search(self, vector: list[float], top_k: int, document_id: int | None = None) -> list[dict[str, Any]]:
        query_filter = None
        if document_id is not None:
            from qdrant_client.models import FieldCondition, Filter, MatchValue

            query_filter = Filter(
                must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))]
            )
        results = self.client.query_points(
            collection_name=COLLECTION,
            query=vector,
            limit=top_k,
            query_filter=query_filter,
            with_payload=True,
        )
        hits = []
        for item in results.points:
            hits.append(
                {
                    "id": str(item.id),
                    "score": float(item.score or 0),
                    "payload": item.payload or {},
                }
            )
        return hits

    def delete_by_document(self, document_id: int) -> None:
        from qdrant_client.models import FieldCondition, Filter, MatchValue

        self.client.delete(
            collection_name=COLLECTION,
            points_selector=Filter(
                must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))]
            ),
        )
