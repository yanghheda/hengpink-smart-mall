import { describe, expect, it, vi } from "vitest";

import { startDecision } from "../src/decision/startDecision";

describe("启动决策分析", () => {
  it("只提交用户需求并携带 H5 会话", async () => {
    const fetcher = vi.fn(
      async (_url: string | URL | Request, init?: RequestInit) =>
        new Response(
          JSON.stringify({
            data: {
              sessionId: "SESSION-1",
              runId: "RUN-1",
              runVersion: 1,
              status: "RUNNING",
            },
          }),
          { status: 200, headers: { "content-type": "application/json" } },
        ),
    ) as unknown as typeof fetch;

    const result = await startDecision({
      requirement: "给父母买手机",
      accessToken: "h5-token",
      fetcher,
    });

    expect(result.sessionId).toBe("SESSION-1");
    expect(fetcher).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/v1/decision-sessions",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer h5-token" }),
        body: JSON.stringify({ requirement: "给父母买手机" }),
      }),
    );
  });
});
