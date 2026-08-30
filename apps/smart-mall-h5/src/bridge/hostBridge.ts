export type OpenProductInput = {
  productId: string;
  skuId: string;
};

export interface HostBridge {
  readonly mode: "web" | "react-native";
  readonly capabilities: ReadonlySet<"openProduct" | "openMockCheckout">;
  openProduct(input: OpenProductInput): Promise<void>;
  openMockCheckout(input: { purchaseIntentId: string }): Promise<void>;
  closeSmartMall(): Promise<void>;
}
