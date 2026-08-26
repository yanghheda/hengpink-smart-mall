"""Generated from packages/api-contracts/openapi.yaml. DO NOT EDIT."""

# 契约源摘要：ba69490c24b134415b96a1cc9d99d6eb317b2eb6b1ec5153217c8ca71b46405e
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
