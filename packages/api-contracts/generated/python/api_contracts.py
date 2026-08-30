"""由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。"""

from __future__ import annotations

# 契约源摘要：8b187de1281560d47f983120e9f3d7c03658425386a4d7f0baca1f8eb9778afe
from typing import Literal, NotRequired, TypedDict


class LoginRequest(TypedDict):
    account: str
    password: str
    deviceSessionId: str


class RefreshRequest(TypedDict):
    refreshToken: str


class AuthTokens(TypedDict):
    tokenType: Literal["Bearer"]
    accessToken: str
    accessTokenExpiresAt: str
    refreshToken: str
    refreshTokenExpiresAt: str


class AuthTokenResponse(TypedDict):
    requestId: str
    data: AuthTokens
    meta: ResponseMeta


class CreateSmartMallTicketRequest(TypedDict):
    hostType: Literal["REACT_NATIVE"]
    deviceSessionId: str
    h5Origin: str


class SmartMallTicket(TypedDict):
    ticket: str
    expiresAt: str


class SmartMallTicketResponse(TypedDict):
    requestId: str
    data: SmartMallTicket
    meta: ResponseMeta


class ExchangeSmartMallTicketRequest(TypedDict):
    ticket: str
    hostType: Literal["REACT_NATIVE"]
    deviceSessionId: str
    bridgeVersion: Literal["1.0"]


class H5Session(TypedDict):
    tokenType: Literal["Bearer"]
    accessToken: str
    accessTokenExpiresAt: str
    userContext: dict[str, object]
    hostContext: dict[str, object]


class H5SessionResponse(TypedDict):
    requestId: str
    data: H5Session
    meta: ResponseMeta


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


class DecisionSessionSnapshot(TypedDict):
    sessionId: str
    currentRunId: NotRequired[str | None]
    currentRunVersion: int
    status: Literal[
        "DRAFT",
        "RUNNING",
        "WAITING_CLARIFICATION",
        "COMPLETED",
        "PARTIAL",
        "FAILED",
        "SUPERSEDED",
        "CANCELLED",
    ]
    currentReportVersion: NotRequired[int | None]


class DecisionSessionSnapshotResponse(TypedDict):
    requestId: str
    data: DecisionSessionSnapshot
    meta: ResponseMeta


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


class ProductComparisonRequest(TypedDict):
    skuIds: list[str]
    mode: Literal["DIFFERENCES", "ALL"]
    relevantAttributeKeys: NotRequired[list[str]]


class ComparedProduct(TypedDict):
    productId: str
    skuId: str
    displayName: str


class ComparisonRow(TypedDict):
    attributeKey: str
    label: str
    unit: NotRequired[str | None]
    values: list[object]


class ProductComparison(TypedDict):
    categoryId: str
    schemaVersion: str
    mode: Literal["DIFFERENCES", "ALL"]
    products: list[ComparedProduct]
    rows: list[ComparisonRow]


class ProductComparisonResponse(TypedDict):
    requestId: str
    data: ProductComparison
    meta: ResponseMeta


class MemoryProposalRequest(TypedDict):
    proposalType: str
    preferenceKey: str
    value: dict[str, object]
    rationaleSummary: str
    scope: NotRequired[Literal["GLOBAL", "CATEGORY", "RECIPIENT_CONTEXT"]]


class MemoryProposal(TypedDict):
    id: str
    userId: str
    sessionId: str
    proposalType: str
    preferenceKey: str
    scope: Literal["GLOBAL", "CATEGORY", "RECIPIENT_CONTEXT"]
    recipientKey: NotRequired[str | None]
    categoryId: NotRequired[str | None]
    value: dict[str, object]
    rationaleSummary: str
    status: Literal["PENDING", "ACCEPTED", "MODIFIED", "REJECTED", "EXPIRED"]
    expiresAt: str
    createdAt: str
    decidedAt: NotRequired[str | None]


class MemoryProposalResponse(TypedDict):
    requestId: str
    data: MemoryProposal
    meta: ResponseMeta


class MemoryDecisionRequest(TypedDict):
    decision: Literal["ACCEPT", "MODIFY", "REJECT"]
    value: NotRequired[dict[str, object]]


class UserPreference(TypedDict):
    id: str
    userId: str
    scope: Literal["GLOBAL", "CATEGORY", "RECIPIENT_CONTEXT"]
    recipientKey: NotRequired[str | None]
    categoryId: NotRequired[str | None]
    preferenceType: str
    preferenceKey: str
    value: dict[str, object]
    sourceSessionId: str
    confirmedAt: str
    expiresAt: str


class MemoryDecisionResult(TypedDict):
    proposal: MemoryProposal
    preference: dict[str, object] | None


class MemoryDecisionResponse(TypedDict):
    requestId: str
    data: MemoryDecisionResult
    meta: ResponseMeta


class FavoriteRequest(TypedDict):
    entityType: Literal["PRODUCT", "REPORT"]
    entityId: str
    reportVersion: NotRequired[int]


class Favorite(TypedDict):
    id: str
    userId: str
    entityType: Literal["PRODUCT", "REPORT"]
    entityId: str
    snapshot: dict[str, object]
    createdAt: str


class FavoriteResponse(TypedDict):
    requestId: str
    data: Favorite
    meta: ResponseMeta


class FavoriteListResponse(TypedDict):
    requestId: str
    data: list[Favorite]
    meta: ResponseMeta


class HistoricalReport(TypedDict):
    sessionId: str
    userId: str
    version: int
    selectedSkuId: str
    report: dict[str, object]
    versions: dict[str, object]
    createdAt: str


class HistoricalReportResponse(TypedDict):
    requestId: str
    data: HistoricalReport
    meta: ResponseMeta


class DeletionResult(TypedDict):
    deleted: bool


class DeletionResponse(TypedDict):
    requestId: str
    data: DeletionResult
    meta: ResponseMeta


class CreatePurchaseIntentRequest(TypedDict):
    sessionId: str
    reportVersion: int
    skuId: str
    pricePlanId: str


class PurchaseIntent(TypedDict):
    id: str
    userId: str
    sessionId: str
    reportVersion: int
    skuId: str
    pricePlanSnapshot: dict[str, object]
    status: Literal["CREATED", "CONFIRMED", "CANCELLED", "EXPIRED"]
    expiresAt: str
    createdAt: str
    confirmedAt: NotRequired[str | None]


class PurchaseIntentResponse(TypedDict):
    requestId: str
    data: PurchaseIntent
    meta: ResponseMeta


class CatalogFact(TypedDict):
    factId: str
    scope: Literal["PRODUCT", "SKU"]
    productId: str
    skuId: NotRequired[str]
    attribute: str
    value: object
    datasetVersion: str


class CatalogFactListResponse(TypedDict):
    requestId: str
    data: list[CatalogFact]
    meta: ResponseMeta


class OfferView(TypedDict):
    offerId: str
    shopId: str
    listPrice: str
    salePrice: str
    additionalFee: str
    currency: str
    validFrom: str
    validTo: str
    datasetVersion: str
    version: int


class OfferList(TypedDict):
    skuId: str
    calculationAt: str
    offers: list[OfferView]


class OfferListResponse(TypedDict):
    requestId: str
    data: OfferList
    meta: ResponseMeta


class DecisionTraceStep(TypedDict):
    sequence: int
    node: str
    status: str
    startedAt: str
    completedAt: NotRequired[str | None]
    durationMs: int
    errorCode: NotRequired[str | None]
    warningCodes: list[str]
    inputSummary: dict[str, object]
    outputSummary: dict[str, object]


class DecisionTrace(TypedDict):
    runId: str
    sessionId: str
    runVersion: int
    status: str
    activeNode: NotRequired[str | None]
    failureCode: NotRequired[str | None]
    degradationCodes: list[str]
    traceId: NotRequired[str | None]
    startedAt: str
    completedAt: NotRequired[str | None]
    modelVersion: NotRequired[str | None]
    promptVersion: NotRequired[str | None]
    datasetVersion: str
    scoringVersion: NotRequired[str | None]
    pricingRuleVersion: NotRequired[str | None]
    embeddingVersion: NotRequired[str | None]
    tokenInput: NotRequired[int | None]
    tokenOutput: NotRequired[int | None]
    estimatedCost: NotRequired[float | None]
    steps: list[DecisionTraceStep]


class DecisionTraceResponse(TypedDict):
    requestId: str
    data: DecisionTrace
    meta: ResponseMeta
