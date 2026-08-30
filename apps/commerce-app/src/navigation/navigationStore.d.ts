export interface ProductSelection {
  productId: string;
  skuId?: string;
}
export interface NavigationState {
  selectedProduct: ProductSelection | null;
  selectedPurchaseIntentId: string | null;
  openProduct(selection: ProductSelection & Record<string, unknown>): void;
  clearProduct(): void;
  openMockCheckout(selection: { purchaseIntentId: string }): void;
  clearMockCheckout(): void;
}
export function createNavigationStore(): import("zustand/vanilla").StoreApi<NavigationState>;
export const navigationStore: import("zustand/vanilla").StoreApi<NavigationState>;
