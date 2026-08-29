// 由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。
// 契约源摘要：2412e4df78c37947f265256c8e7e4d87fe2e74e8c5d29e3fa869a585246f618b

export interface LoginRequest {
  account: string;
  password: string;
  deviceSessionId: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthTokens {
  tokenType: "Bearer";
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface AuthTokenResponse {
  requestId: string;
  data: AuthTokens;
  meta: ResponseMeta;
}

export interface CreateSmartMallTicketRequest {
  hostType: "REACT_NATIVE";
  deviceSessionId: string;
  h5Origin: string;
}

export interface SmartMallTicket {
  ticket: string;
  expiresAt: string;
}

export interface SmartMallTicketResponse {
  requestId: string;
  data: SmartMallTicket;
  meta: ResponseMeta;
}

export interface ExchangeSmartMallTicketRequest {
  ticket: string;
  hostType: "REACT_NATIVE";
  deviceSessionId: string;
  bridgeVersion: "1.0";
}

export interface H5Session {
  tokenType: "Bearer";
  accessToken: string;
  accessTokenExpiresAt: string;
  userContext: Record<string, unknown>;
  hostContext: Record<string, unknown>;
}

export interface H5SessionResponse {
  requestId: string;
  data: H5Session;
  meta: ResponseMeta;
}

export interface ResponseMeta {
  serverTime: string;
}

export interface SuccessEnvelope {
  requestId: string;
  data: Record<string, unknown>;
  meta: ResponseMeta;
}

export interface ErrorDetail {
  field: string;
  reason: string;
}

export interface ApiError {
  code: string;
  message: string;
  retryable: boolean;
  details?: Array<ErrorDetail>;
}

export interface ErrorEnvelope {
  requestId: string;
  error: ApiError;
}

export interface DecisionSessionSnapshot {
  sessionId: string;
  currentRunId?: string | null;
  currentRunVersion: number;
  status: "DRAFT" | "RUNNING" | "WAITING_CLARIFICATION" | "COMPLETED" | "PARTIAL" | "FAILED" | "SUPERSEDED" | "CANCELLED";
  currentReportVersion?: number | null;
}

export interface DecisionSessionSnapshotResponse {
  requestId: string;
  data: DecisionSessionSnapshot;
  meta: ResponseMeta;
}

export interface HealthData {
  status: "UP";
  service: string;
  contractVersion: string;
}

export interface HealthResponse {
  requestId: string;
  data: HealthData;
  meta: ResponseMeta;
}

export interface ProductSummary {
  productId: string;
  categoryId: string;
  categoryName: string;
  brand: string;
  model: string;
  displayName: string;
  subtitle?: string;
  datasetVersion: string;
  simulated: boolean;
  skuCount: number;
}

export interface ProductPage {
  items: Array<ProductSummary>;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProductPageResponse {
  requestId: string;
  data: ProductPage;
  meta: ResponseMeta;
}

export interface SkuDetail {
  skuId: string;
  skuCode: string;
  displayName: string;
  attributes: Record<string, unknown>;
  stockStatus: string;
  stockQuantity: number;
  warrantyMonths: number;
}

export interface ProductDetail {
  productId: string;
  categoryId: string;
  categoryName: string;
  brand: string;
  model: string;
  displayName: string;
  subtitle?: string;
  canonicalSpecs: Record<string, unknown>;
  sellingPoints: Array<string>;
  limitations: Array<string>;
  warrantySummary?: string;
  datasetVersion: string;
  simulated: boolean;
  skus: Array<SkuDetail>;
  selectedSku?: SkuDetail;
}

export interface ProductDetailResponse {
  requestId: string;
  data: ProductDetail;
  meta: ResponseMeta;
}

export interface AttributeConstraint {
  attribute: string;
  operator: ">=" | "<=" | "=";
  value: unknown;
}

export interface CatalogSearchRequest {
  categoryId: string;
  minPrice?: number;
  maxPrice?: number;
  inStockOnly: boolean;
  attributes: Array<AttributeConstraint>;
}

export interface SearchCandidate {
  productId: string;
  skuId: string;
  displayName: string;
  categoryId: string;
  price?: number;
  stockStatus: string;
  stockQuantity: number;
  attributes: Record<string, unknown>;
}

export interface RejectedCandidate {
  candidate: SearchCandidate;
  reasonCodes: Array<string>;
}

export interface CatalogSearchResult {
  matched: Array<SearchCandidate>;
  rejected: Array<RejectedCandidate>;
}

export interface CatalogSearchResponse {
  requestId: string;
  data: CatalogSearchResult;
  meta: ResponseMeta;
}

export interface CatalogFact {
  factId: string;
  scope: "PRODUCT" | "SKU";
  productId: string;
  skuId?: string;
  attribute: string;
  value: unknown;
  datasetVersion: string;
}

export interface CatalogFactListResponse {
  requestId: string;
  data: Array<CatalogFact>;
  meta: ResponseMeta;
}

export interface OfferView {
  offerId: string;
  shopId: string;
  listPrice: string;
  salePrice: string;
  additionalFee: string;
  currency: string;
  validFrom: string;
  validTo: string;
  datasetVersion: string;
  version: number;
}

export interface OfferList {
  skuId: string;
  calculationAt: string;
  offers: Array<OfferView>;
}

export interface OfferListResponse {
  requestId: string;
  data: OfferList;
  meta: ResponseMeta;
}
