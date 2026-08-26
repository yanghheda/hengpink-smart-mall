// 由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。
// 契约源摘要：ba69490c24b134415b96a1cc9d99d6eb317b2eb6b1ec5153217c8ca71b46405e

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
