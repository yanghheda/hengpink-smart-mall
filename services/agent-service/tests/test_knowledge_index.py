from app.knowledge.indexing import build_points, reconcile_index


def _chunk(evidence_id: str = "EV-1", content_hash: str = "a" * 64) -> dict:
    return {
        "chunk_id": f"{evidence_id}-C001",
        "evidence_id": evidence_id,
        "product_id": "P-1",
        "sku_id": "S-1",
        "category_id": "PHONE",
        "source_type": "SIMULATED_REVIEW",
        "topic": "battery",
        "sentiment": "POSITIVE",
        "trust_level": 0.7,
        "published_at": "2026-08-01T00:00:00Z",
        "dataset_version": "commerce-demo-2026.08.1",
        "content": "续航稳定。",
        "content_hash": content_hash,
        "is_simulated": True,
    }


def test_points_are_deterministic_and_carry_required_payload() -> None:
    assert build_points([_chunk()]) == build_points([_chunk()])
    payload = build_points([_chunk()])[0]["payload"]
    assert payload["embedding_version"] == "fixture-hash-v1"
    assert payload["content_hash"] == "a" * 64
    assert payload["sku_id"] == "S-1"


def test_reconciliation_rejects_count_or_hash_drift() -> None:
    expected = [_chunk()]
    assert reconcile_index(expected, build_points(expected)) == {
        "count": 1,
        "hashes_match": True,
    }

    wrong_hash = build_points([_chunk(content_hash="b" * 64)])
    try:
        reconcile_index(expected, wrong_hash)
    except ValueError as error:
        assert "哈希" in str(error)
    else:
        raise AssertionError("哈希漂移必须阻止索引验收")
