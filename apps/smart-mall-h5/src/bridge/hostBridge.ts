export type OpenProductInput = {
  productId: string;
  skuId: string;
};

export interface HostBridge {
  readonly mode: "web" | "react-native";
  readonly capabilities: ReadonlySet<"openProduct">;
  openProduct(input: OpenProductInput): Promise<void>;
  closeSmartMall(): Promise<void>;
}
