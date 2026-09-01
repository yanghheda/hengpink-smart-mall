import { describe, expect, it, vi } from "vitest";

import {
  loadDecisionReport,
  reweightDecisionReport,
} from "../src/decision/decisionReport";

describe("决策报告客户端", () => {
  it("按权威版本读取报告并携带 H5 会话", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ data: { sessionId: "S-1", version: 2 } }),
        ),
      );

    await loadDecisionReport("S-1", 2, "token", fetcher);

    expect(fetcher).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/v1/decision-sessions/S-1/reports/2",
      { headers: { Authorization: "Bearer token" } },
    );
  });

  it("调权只提交报告版本和五维权重", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ data: { version: 3 } })),
      );
    const weights = {
      NEED_MATCH: 2,
      PRICE_VALUE: 3,
      REVIEW_QUALITY: 1,
      PROMOTION_VALUE: 0,
      RELIABILITY: 4,
    };

    await reweightDecisionReport("S-1", 2, weights, "token", fetcher);

    expect(fetcher).toHaveBeenCalledWith(
      "http://127.0.0.1:8080/api/v1/decision-sessions/S-1/weights",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ reportVersion: 2, weights }),
      }),
    );
  });
});
