import type { HostBridge, OpenProductInput } from "./hostBridge";

type HostBridgeWebOptions = {
  navigate: (path: string) => void;
};

export function createHostBridgeWeb({
  navigate,
}: HostBridgeWebOptions): HostBridge {
  return {
    mode: "web",
    capabilities: new Set(["openProduct"]),
    async openProduct({ productId, skuId }: OpenProductInput) {
      navigate(
        `/standalone/products/${encodeURIComponent(productId)}?skuId=${encodeURIComponent(skuId)}`,
      );
    },
    async closeSmartMall() {
      throw new Error("Standalone 模式无法关闭宿主商城");
    },
  };
}
