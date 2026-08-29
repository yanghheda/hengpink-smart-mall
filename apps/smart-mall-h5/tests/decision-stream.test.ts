import { describe, expect, it, vi } from "vitest";

import {
  consumeDecisionStream,
  fetchDecisionSessionSnapshot,
  recoverDecisionSession,
} from "../src/decision/decisionStream";

function responseFrom(chunks: string[]) {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
        controller.close();
      },
    }),
    { status: 200, headers: { "Content-Type": "text/event-stream" } },
  );
}

describe("Decision SSE", () => {
  it("跨网络分块解析事件，并忽略已处理 ID", async () => {
    const onEvent = vi.fn();
    await consumeDecisionStream({
      url: "/stream",
      lastEventId: "1-0",
      fetcher: vi
        .fn()
        .mockResolvedValue(
          responseFrom([
            'id: 1-0\nevent: run.stage\ndata: {"progress":20}\n\nid: 2-',
            '0\nevent: run.stage\ndata: {"progress":40}\n\n',
          ]),
        ),
      onEvent,
    });

    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventId: "2-0", progress: 40 }),
    );
  });

  it("重连时用 Last-Event-ID 请求头续读", async () => {
    const fetcher = vi.fn().mockResolvedValue(responseFrom([]));

    await consumeDecisionStream({
      url: "/stream",
      accessToken: "h5-token",
      lastEventId: "8-2",
      fetcher,
      onEvent: vi.fn(),
    });

    expect(fetcher).toHaveBeenCalledWith(
      "/stream",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer h5-token",
          "Last-Event-ID": "8-2",
        }),
      }),
    );
  });

  it("进度回退事件不触发 UI 副作用", async () => {
    const onEvent = vi.fn();
    await consumeDecisionStream({
      url: "/stream",
      fetcher: vi
        .fn()
        .mockResolvedValue(
          responseFrom([
            'id: 1-0\nevent: run.stage\ndata: {"progress":65}\n\n' +
              'id: 2-0\nevent: run.stage\ndata: {"progress":40}\n\n',
          ]),
        ),
      onEvent,
    });

    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onEvent.mock.calls[0][0].progress).toBe(65);
  });

  it("连续三次 SSE 失败后显式切换轮询，并在终态停止", async () => {
    const fetchSnapshot = vi
      .fn()
      .mockResolvedValueOnce({
        sessionId: "session-1",
        currentRunId: "run-1",
        currentRunVersion: 1,
        status: "RUNNING",
        currentReportVersion: null,
      })
      .mockResolvedValueOnce({
        sessionId: "session-1",
        currentRunId: "run-1",
        currentRunVersion: 1,
        status: "COMPLETED",
        currentReportVersion: 1,
      });
    const consumeStream = vi.fn().mockRejectedValue(new Error("SSE 不可用"));
    const onTransportState = vi.fn();

    const result = await recoverDecisionSession({
      fetchSnapshot,
      consumeStream,
      wait: vi.fn().mockResolvedValue(undefined),
      onSnapshot: vi.fn(),
      onTransportState,
    });

    expect(consumeStream).toHaveBeenCalledTimes(3);
    expect(fetchSnapshot).toHaveBeenCalledTimes(2);
    expect(onTransportState).toHaveBeenCalledWith("POLLING");
    expect(result.status).toBe("COMPLETED");
    expect(onTransportState).toHaveBeenLastCalledWith("STOPPED");
  });

  it("页面刷新或事件过期时直接用 MySQL 快照恢复终态", async () => {
    const terminalSnapshot = {
      sessionId: "session-1",
      currentRunId: "run-1",
      currentRunVersion: 1,
      status: "FAILED" as const,
      currentReportVersion: null,
    };
    const consumeStream = vi.fn();

    const result = await recoverDecisionSession({
      fetchSnapshot: vi.fn().mockResolvedValue(terminalSnapshot),
      consumeStream,
      wait: vi.fn(),
      onSnapshot: vi.fn(),
      onTransportState: vi.fn(),
    });

    expect(consumeStream).not.toHaveBeenCalled();
    expect(result).toEqual(terminalSnapshot);
  });

  it("快照查询携带 H5 认证且只返回 data", async () => {
    const snapshot = {
      sessionId: "session-1",
      currentRunId: "run-1",
      currentRunVersion: 1,
      status: "RUNNING" as const,
      currentReportVersion: null,
    };
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ data: snapshot })));

    await expect(
      fetchDecisionSessionSnapshot({
        url: "/api/v1/decision-sessions/session-1",
        accessToken: "h5-token",
        fetcher,
      }),
    ).resolves.toEqual(snapshot);
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/decision-sessions/session-1",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer h5-token",
        }),
      }),
    );
  });
});
