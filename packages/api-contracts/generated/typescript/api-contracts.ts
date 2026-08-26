// 由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。
// 契约源摘要：c5c2d62e970dc571d353e368116f2caeff02b20d3cf2e4158d7a870af7240fe4

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
