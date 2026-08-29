from __future__ import annotations

import hashlib
import math
from typing import Any

EMBEDDING_MODEL = "fixture-hash"
EMBEDDING_VERSION = "fixture-hash-v1"
VECTOR_SIZE = 32


def _point_id(chunk_id: str) -> str:
    digest = hashlib.sha256(chunk_id.encode("utf-8")).hexdigest()[:32]
    return f"{digest[:8]}-{digest[8:12]}-{digest[12:16]}-{digest[16:20]}-{digest[20:]}"


def _fixture_embedding(content: str) -> list[float]:
    """生成可复现的离线测试向量；该向量不代表生产语义质量。"""
    digest = hashlib.sha256(content.encode("utf-8")).digest()
    values = [(byte - 127.5) / 127.5 for byte in digest]
    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [value / norm for value in values]


def build_points(chunks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    points = []
    for chunk in chunks:
        payload = dict(chunk)
        payload["embedding_model"] = EMBEDDING_MODEL
        payload["embedding_version"] = EMBEDDING_VERSION
        points.append(
            {
                "id": _point_id(chunk["chunk_id"]),
                "vector": _fixture_embedding(chunk["content"]),
                "payload": payload,
            }
        )
    return points


def reconcile_index(
    mysql_chunks: list[dict[str, Any]], qdrant_points: list[dict[str, Any]]
) -> dict[str, int | bool]:
    if len(mysql_chunks) != len(qdrant_points):
        raise ValueError("索引数量与 MySQL 元数据不一致")
    expected = {(chunk["chunk_id"], chunk["content_hash"]) for chunk in mysql_chunks}
    actual = {
        (point["payload"]["chunk_id"], point["payload"]["content_hash"]) for point in qdrant_points
    }
    if expected != actual:
        raise ValueError("索引内容哈希与 MySQL 元数据不一致")
    return {"count": len(expected), "hashes_match": True}
