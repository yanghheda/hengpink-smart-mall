import { describe, expect, it, vi } from "vitest";

import { consumeDecisionStream } from "../src/decision/decisionStream";

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
});
