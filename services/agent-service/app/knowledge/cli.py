from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from typing import Any

import mysql.connector

from .indexing import EMBEDDING_MODEL, EMBEDDING_VERSION, VECTOR_SIZE, build_points, reconcile_index

COLLECTION = "product_knowledge_v1"
INDEX_FIELDS = {
    "dataset_version": "keyword",
    "category_id": "keyword",
    "product_id": "keyword",
    "sku_id": "keyword",
    "source_type": "keyword",
    "topic": "keyword",
    "sentiment": "keyword",
    "trust_level": "float",
    "published_at": "datetime",
}


def _required(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"缺少环境变量：{name}")
    return value


def _mysql_chunks(dataset_version: str) -> list[dict[str, Any]]:
    connection = mysql.connector.connect(
        host=_required("MYSQL_HOST"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=_required("MYSQL_USERNAME"),
        password=_required("MYSQL_PASSWORD"),
        database=_required("MYSQL_DATABASE"),
    )
    try:
        cursor = connection.cursor(dictionary=True)
        cursor.execute(
            """SELECT id AS chunk_id, evidence_id, product_id, sku_id, category_id, source_type,
            topic, sentiment, CAST(trust_level AS DOUBLE) AS trust_level,
            DATE_FORMAT(published_at, '%Y-%m-%dT%H:%i:%s.000Z') AS published_at,
            dataset_version, content, content_hash, is_simulated
            FROM knowledge_documents WHERE dataset_version = %s ORDER BY id""",
            (dataset_version,),
        )
        return [dict(row) for row in cursor.fetchall()]
    finally:
        connection.close()


def _request(method: str, path: str, body: dict[str, Any]) -> dict[str, Any]:
    headers = {"Content-Type": "application/json"}
    if api_key := os.getenv("QDRANT_API_KEY"):
        headers["api-key"] = api_key
    request = urllib.request.Request(
        f"{_required('QDRANT_URL').rstrip('/')}{path}",
        data=json.dumps(body).encode(),
        headers=headers,
        method=method,
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response)


def _ensure_collection() -> None:
    try:
        _request(
            "PUT",
            f"/collections/{COLLECTION}",
            {"vectors": {"size": VECTOR_SIZE, "distance": "Cosine"}},
        )
    except urllib.error.HTTPError as error:
        if error.code != 409:
            raise
    for field, schema in INDEX_FIELDS.items():
        _request(
            "PUT",
            f"/collections/{COLLECTION}/index?wait=true",
            {"field_name": field, "field_schema": schema},
        )


def _read_points(dataset_version: str) -> list[dict[str, Any]]:
    response = _request(
        "POST",
        f"/collections/{COLLECTION}/points/scroll",
        {
            "filter": {"must": [{"key": "dataset_version", "match": {"value": dataset_version}}]},
            "limit": 1000,
            "with_payload": True,
            "with_vector": False,
        },
    )
    return response["result"]["points"]


def main() -> None:
    dataset_version = _required("DATASET_VERSION")
    chunks = _mysql_chunks(dataset_version)
    if not chunks:
        raise RuntimeError(f"MySQL 中没有待索引知识元数据：{dataset_version}")
    _ensure_collection()
    _request("PUT", f"/collections/{COLLECTION}/points?wait=true", {"points": build_points(chunks)})
    report = reconcile_index(chunks, _read_points(dataset_version))
    print(
        json.dumps(
            {
                "collection": COLLECTION,
                "dataset_version": dataset_version,
                "embedding_model": EMBEDDING_MODEL,
                "embedding_version": EMBEDDING_VERSION,
                **report,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"知识索引失败：{error}", file=sys.stderr)
        raise
