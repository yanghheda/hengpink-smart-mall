export class ApiError extends Error {
  status: number;
  code: string;
}
export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body: unknown): Promise<T>;
}
export function createApiClient(options: {
  baseUrl: string;
  getAccessToken?: () => string | null;
  onSessionExpired?: () => void | Promise<void>;
  fetchImpl?: typeof fetch;
}): ApiClient;
