export type BridgeController = {
  onMessage(
    raw: string,
    origin: string,
  ): Promise<{
    ok: boolean;
    error?: string;
    duplicate?: boolean;
  }>;
};

export function createHostBridgeController(options: {
  allowedOrigin: string;
  now?: () => number;
  createTicket: () => Promise<{ ticket: string; expiresAt?: string }>;
  send: (message: unknown) => void;
  openProduct: (selection: { productId: string; skuId: string }) => void;
  openMockCheckout: (selection: { purchaseIntentId: string }) => void;
  theme?: "light" | "dark" | "system";
  locale?: string;
}): BridgeController;
