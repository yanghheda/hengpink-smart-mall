import { createStore } from "zustand/vanilla";
export function createNavigationStore() {
  return createStore((set) => ({
    selectedProduct: null,
    openProduct: ({ productId, skuId }) =>
      set({ selectedProduct: { productId, skuId } }),
    clearProduct: () => set({ selectedProduct: null }),
  }));
}
export const navigationStore = createNavigationStore();
