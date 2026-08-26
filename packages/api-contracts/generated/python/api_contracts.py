"""由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。"""

# 契约源摘要：c5c2d62e970dc571d353e368116f2caeff02b20d3cf2e4158d7a870af7240fe4
from typing import Literal, NotRequired, TypedDict


class ResponseMeta(TypedDict):
    serverTime: str


class SuccessEnvelope(TypedDict):
    requestId: str
    data: dict[str, object]
    meta: ResponseMeta


class ErrorDetail(TypedDict):
    field: str
    reason: str


class ApiError(TypedDict):
    code: str
    message: str
    retryable: bool
    details: NotRequired[list[ErrorDetail]]


class ErrorEnvelope(TypedDict):
    requestId: str
    error: ApiError


class HealthData(TypedDict):
    status: Literal["UP"]
    service: str
    contractVersion: str


class HealthResponse(TypedDict):
    requestId: str
    data: HealthData
    meta: ResponseMeta


class ProductSummary(TypedDict):
    productId: str
    categoryId: str
    categoryName: str
    brand: str
    model: str
    displayName: str
    subtitle: NotRequired[str]
    datasetVersion: str
    simulated: bool
    skuCount: int


class ProductPage(TypedDict):
    items: list[ProductSummary]
    page: int
    size: int
    totalElements: int
    totalPages: int


class ProductPageResponse(TypedDict):
    requestId: str
    data: ProductPage
    meta: ResponseMeta


class SkuDetail(TypedDict):
    skuId: str
    skuCode: str
    displayName: str
    attributes: dict[str, object]
    stockStatus: str
    stockQuantity: int
    warrantyMonths: int


class ProductDetail(TypedDict):
    productId: str
    categoryId: str
    categoryName: str
    brand: str
    model: str
    displayName: str
    subtitle: NotRequired[str]
    canonicalSpecs: dict[str, object]
    sellingPoints: list[str]
    limitations: list[str]
    warrantySummary: NotRequired[str]
    datasetVersion: str
    simulated: bool
    skus: list[SkuDetail]
    selectedSku: NotRequired[SkuDetail]


class ProductDetailResponse(TypedDict):
    requestId: str
    data: ProductDetail
    meta: ResponseMeta


class AttributeConstraint(TypedDict):
    attribute: str
    operator: Literal[">=", "<=", "="]
    value: object


class CatalogSearchRequest(TypedDict):
    categoryId: str
    minPrice: NotRequired[float]
    maxPrice: NotRequired[float]
    inStockOnly: bool
    attributes: list[AttributeConstraint]


class SearchCandidate(TypedDict):
    productId: str
    skuId: str
    displayName: str
    categoryId: str
    price: NotRequired[float]
    stockStatus: str
    stockQuantity: int
    attributes: dict[str, object]


class RejectedCandidate(TypedDict):
    candidate: SearchCandidate
    reasonCodes: list[str]


class CatalogSearchResult(TypedDict):
    matched: list[SearchCandidate]
    rejected: list[RejectedCandidate]


class CatalogSearchResponse(TypedDict):
    requestId: str
    data: CatalogSearchResult
    meta: ResponseMeta
