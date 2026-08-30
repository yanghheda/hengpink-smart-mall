import { createStore } from "zustand/vanilla";
export function createNavigationStore() {
  return createStore((set) => ({
    selectedProduct: null,
    selectedPurchaseIntentId: null,
    openProduct: ({ productId, skuId }) =>
      set({ selectedProduct: { productId, skuId } }),
    clearProduct: () => set({ selectedProduct: null }),
    openMockCheckout: ({ purchaseIntentId }) =>
      set({ selectedPurchaseIntentId: purchaseIntentId }),
    clearMockCheckout: () => set({ selectedPurchaseIntentId: null }),
  }));
}
export const navigationStore = createNavigationStore();
