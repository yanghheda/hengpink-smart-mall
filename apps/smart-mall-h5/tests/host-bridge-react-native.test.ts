import { describe, expect, it, vi } from "vitest";

import { createHostBridgeReactNative } from "../src/bridge/hostBridgeReactNative";

const BOOTSTRAP_ID = "01J00000000000000000000002";

function bootstrap(ticket = "opaque-ticket-value") {
  return {
    protocol: "hengpick.host-bridge" as const,
    version: "1.0" as const,
    messageId: BOOTSTRAP_ID,
    kind: "event" as const,
    action: "bridge.bootstrap" as const,
    timestamp: 1_000,
    payload: {
      smartMallTicket: ticket,
      theme: "light" as const,
      locale: "zh-CN",
    },
  };
}

describe("HostBridgeReactNative", () => {
  it("每次启动发送 ready，bootstrap 后兑换并只在内存保存 H5 Access Token", async () => {
    const postMessage = vi.fn();
    const exchangeTicket = vi.fn().mockResolvedValue({
      accessToken: "h5-access-token",
      accessTokenExpiresAt: "2026-08-28T10:05:00Z",
      userContext: { userId: "user-1", role: "DEMO_USER" },
    });
    const bridge = createHostBridgeReactNative({
      postMessage,
      exchangeTicket,
      now: () => 1_000,
      createMessageId: () => "01J00000000000000000000003",
      handshakeTimeoutMs: 500,
    });

    bridge.start();
    expect(JSON.parse(postMessage.mock.calls[0][0]).action).toBe(
      "bridge.ready",
    );
    await bridge.receive(JSON.stringify(bootstrap()));

    expect(exchangeTicket).toHaveBeenCalledWith("opaque-ticket-value");
    expect(bridge.getSnapshot()).toMatchObject({
      status: "initialized",
      accessToken: "h5-access-token",
    });
    bridge.stop();
  });

  it("500ms 未握手时明确降级为 standalone", () => {
    vi.useFakeTimers();
    const bridge = createHostBridgeReactNative({
      postMessage: vi.fn(),
      exchangeTicket: vi.fn(),
      now: () => 1_000,
      createMessageId: () => "01J00000000000000000000003",
      handshakeTimeoutMs: 500,
    });

    bridge.start();
    vi.advanceTimersByTime(500);

    expect(bridge.getSnapshot().status).toBe("standalone");
    bridge.stop();
    vi.useRealTimers();
  });

  it("Ticket 过期时保留显式错误，不伪装初始化成功", async () => {
    const bridge = createHostBridgeReactNative({
      postMessage: vi.fn(),
      exchangeTicket: vi
        .fn()
        .mockRejectedValue(new Error("Ticket 已过期或已使用")),
      now: () => 1_000,
      createMessageId: () => "01J00000000000000000000003",
      handshakeTimeoutMs: 500,
    });

    bridge.start();
    await bridge.receive(JSON.stringify(bootstrap()));

    expect(bridge.getSnapshot()).toMatchObject({
      status: "error",
      error: "Ticket 已过期或已使用",
    });
    bridge.stop();
  });

  it("openProduct 只发送两个资源 ID，不携带 price", async () => {
    const postMessage = vi.fn();
    const bridge = createHostBridgeReactNative({
      postMessage,
      exchangeTicket: vi.fn(),
      now: () => 1_000,
      createMessageId: () => "01J00000000000000000000004",
      handshakeTimeoutMs: 500,
    });

    bridge.start();
    const request = bridge
      .openProduct({ productId: "product-1", skuId: "sku-1" })
      .catch(() => undefined);
    const message = JSON.parse(postMessage.mock.calls[1][0]);

    expect(message.payload).toEqual({ productId: "product-1", skuId: "sku-1" });
    expect(message.payload).not.toHaveProperty("price");
    bridge.stop();
    await request;
  });

  it("openMockCheckout 只发送 PurchaseIntent ID", async () => {
    const postMessage = vi.fn();
    const bridge = createHostBridgeReactNative({
      postMessage,
      exchangeTicket: vi.fn(),
      now: () => 1_000,
      createMessageId: () => "01J00000000000000000000005",
    });
    bridge.start();
    const request = bridge
      .openMockCheckout({ purchaseIntentId: "intent-1" })
      .catch(() => undefined);
    expect(JSON.parse(postMessage.mock.calls[1][0]).payload).toEqual({
      purchaseIntentId: "intent-1",
    });
    bridge.stop();
    await request;
  });
});
