export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export function createApiClient({
  baseUrl,
  getAccessToken = () => null,
  onSessionExpired,
  fetchImpl = fetch,
}) {
  async function request(path, init = {}) {
    const accessToken = getAccessToken();
    const headers = new Headers(init.headers);
    headers.set("accept", "application/json");
    if (init.body) headers.set("content-type", "application/json");
    if (accessToken) headers.set("authorization", `Bearer ${accessToken}`);
    const response = await fetchImpl(`${baseUrl}${path}`, { ...init, headers });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401 && accessToken && onSessionExpired)
        await onSessionExpired();
      throw new ApiError(
        response.status,
        payload.error?.code ?? "API_REQUEST_FAILED",
        payload.error?.message ?? "请求失败，请稍后重试",
      );
    }
    return payload.data;
  }
  return {
    get: (path) => request(path),
    post: (path, body) =>
      request(path, { method: "POST", body: JSON.stringify(body) }),
  };
}
