export type StartedDecision = {
  sessionId: string;
  runId: string;
  runVersion: number;
  status: "RUNNING";
};

export async function startDecision({
  requirement,
  accessToken,
  fetcher = fetch,
}: {
  requirement: string;
  accessToken: string;
  fetcher?: typeof fetch;
}) {
  const apiBaseUrl =
    import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";
  const response = await fetcher(`${apiBaseUrl}/api/v1/decision-sessions`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ requirement }),
  });
  const payload = (await response.json()) as {
    data?: StartedDecision;
    error?: { message?: string };
  };
  if (!response.ok || !payload.data) {
    throw new Error(
      payload.error?.message ?? `分析启动失败: ${response.status}`,
    );
  }
  return payload.data;
}

export async function continueDecision({ sessionId, content, accessToken, fetcher = fetch }: {
  sessionId: string; content: string; accessToken: string; fetcher?: typeof fetch;
}) {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";
  const response = await fetcher(`${apiBaseUrl}/api/v1/decision-sessions/${encodeURIComponent(sessionId)}/messages`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ content }),
  });
  const payload = (await response.json()) as { data?: StartedDecision; error?: { message?: string } };
  if (!response.ok || !payload.data) throw new Error(payload.error?.message ?? `继续分析失败: ${response.status}`);
  return payload.data;
}
