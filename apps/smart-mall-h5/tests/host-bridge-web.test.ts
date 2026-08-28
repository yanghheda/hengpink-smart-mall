import { describe, expect, it, vi } from "vitest";

import { createHostBridgeWeb } from "../src/bridge/hostBridgeWeb";

describe("HostBridgeWeb", () => {
  it("在独立模式把商品打开到 H5 预览路由", async () => {
    const navigate = vi.fn();
    const bridge = createHostBridgeWeb({ navigate });

    await bridge.openProduct({ productId: "product/1", skuId: "sku 2" });

    expect(bridge.mode).toBe("web");
    expect(bridge.capabilities.has("openProduct")).toBe(true);
    expect(navigate).toHaveBeenCalledWith(
      "/standalone/products/product%2F1?skuId=sku%202",
    );
  });

  it("明确拒绝本阶段尚未实现的宿主操作", async () => {
    const bridge = createHostBridgeWeb({ navigate: vi.fn() });

    await expect(bridge.closeSmartMall()).rejects.toThrow(
      "Standalone 模式无法关闭宿主商城",
    );
  });
});
