from __future__ import annotations

import os

from openai import OpenAI


class EmbeddingClient:
    def __init__(self) -> None:
        base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com")
        base_url = base_url.rstrip("/")
        if not base_url.endswith("/v1"):
            base_url = base_url + "/v1"
        api_key = os.getenv("OPENAI_API_KEY", "")
        self.model = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
        self.client = OpenAI(api_key=api_key, base_url=base_url)

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        response = self.client.embeddings.create(model=self.model, input=texts)
        ordered = sorted(response.data, key=lambda item: item.index)
        return [item.embedding for item in ordered]
