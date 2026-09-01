from copy import deepcopy
from typing import Any

from app.tools.client import CommerceToolClient

DATASET_VERSION = "commerce-demo-2026.08.1"


class DeterministicToolTransport:
    """商品主链测试使用的确定性 Java Tool 替身。"""

    def __init__(self, candidate_count: int = 3) -> None:
        self.candidate_count = candidate_count
        self.calls: list[tuple[str, dict[str, Any], float]] = []

    def post(self, path: str, payload: dict[str, Any], timeout_seconds: float) -> dict[str, Any]:
        self.calls.append((path, deepcopy(payload), timeout_seconds))
        sku_ids = [f"SKU-PHONE-{index + 1}" for index in range(self.candidate_count)]
        if path.endswith("search-products"):
            data = {
                "matchedCandidates": [{"skuId": sku_id} for sku_id in sku_ids],
                "rejectedCandidates": [],
            }
        elif path.endswith("get-product-specs"):
            requested = payload["input"]["candidates"]
            data = {
                "candidates": [
                    {
                        "productId": f"P-{index + 1}",
                        "skuId": candidate["skuId"],
                        "displayName": f"手机 {index + 1}",
                        "attributes": {"storageGb": 256},
                    }
                    for index, candidate in enumerate(requested)
                ]
            }
        elif path.endswith("get-price-offers"):
            data = {
                "offers": [
                    {"skuId": sku_id, "offerId": f"O-{sku_id}", "salePrice": "2999.00"}
                    for sku_id in payload["input"]["skuIds"]
                ]
            }
        elif path.endswith("calculate-final-price"):
            data = {
                "pricePlans": {
                    offer["skuId"]: [
                        {"pricePlanId": f"PP-{offer['skuId']}", "finalPrice": offer["salePrice"]}
                    ]
                    for offer in payload["input"]["offers"]
                }
            }
        elif path.endswith("score-candidates"):
            data = {
                "scoreCards": [
                    {"skuId": candidate["skuId"], "finalScore": str(91 - index)}
                    for index, candidate in enumerate(payload["input"]["candidates"])
                ]
            }
        else:
            raise AssertionError(f"未预期的 Tool 路径: {path}")
        return {
            "status": "SUCCESS",
            "data": data,
            "sourceVersion": payload["datasetVersion"],
            "updatedAt": "2026-08-25T02:00:00Z",
            "confidence": 1.0,
            "warnings": [],
            "errorCode": None,
        }


def deterministic_tool_client(candidate_count: int = 3) -> CommerceToolClient:
    return CommerceToolClient(DeterministicToolTransport(candidate_count))
