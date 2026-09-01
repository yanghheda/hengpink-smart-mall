import { rememberH5AccessToken } from "./decisionTrace";

type Envelope<T> = { data?: T; error?: { message?: string } };

async function requireData<T>(response: Response, fallback: string) {
  const payload = (await response.json()) as Envelope<T>;
  if (!response.ok || !payload.data) {
    throw new Error(payload.error?.message ?? fallback);
  }
  return payload.data;
}

/** 独立演示只使用公开测试账号走完整 Ticket 流程，不持久化任何 Token。 */
export async function createStandaloneH5Session(fetcher: typeof fetch = fetch) {
  const apiBaseUrl =
    import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";
  const deviceSessionId = `smart-mall-standalone-${crypto.randomUUID()}`;
  const login = await requireData<{ accessToken: string }>(
    await fetcher(`${apiBaseUrl}/api/v1/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        account: "demo_user",
        password: "demo123456",
        deviceSessionId,
      }),
    }),
    "演示账号登录失败",
  );
  const ticket = await requireData<{ ticket: string }>(
    await fetcher(`${apiBaseUrl}/api/v1/smart-mall/tickets`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${login.accessToken}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        hostType: "REACT_NATIVE",
        deviceSessionId,
        h5Origin: window.location.origin,
      }),
    }),
    "演示 Ticket 创建失败",
  );
  const session = await requireData<{ accessToken: string }>(
    await fetcher(`${apiBaseUrl}/api/v1/smart-mall/sessions/exchange`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        ticket: ticket.ticket,
        hostType: "REACT_NATIVE",
        deviceSessionId,
        bridgeVersion: "1.0",
      }),
    }),
    "演示 H5 会话兑换失败",
  );
  rememberH5AccessToken(session.accessToken);
  return session.accessToken;
}
