export type SessionStatus = "restoring" | "anonymous" | "authenticated";

export type RootStackParams = {
  Home: undefined;
  ProductDetail: undefined;
  SmartMall: undefined;
  MockCheckout: undefined;
};

export type ProductSummary = {
  productId: string;
  categoryId: string;
  categoryName: string;
  brand: string;
  model: string;
  displayName: string;
  subtitle?: string;
  skuCount: number;
};

export type ProductPage = {
  items: ProductSummary[];
  totalElements: number;
};

export type SkuDetail = {
  skuId: string;
  displayName: string;
};

export type ProductDetail = ProductSummary & {
  skus: SkuDetail[];
};

export type OfferList = {
  skuId: string;
  offers: Array<{ offerId: string; salePrice: string; currency: string }>;
};

export type PurchaseIntent = {
  id: string;
  skuId: string;
  status: "CREATED" | "CONFIRMED" | "CANCELLED" | "EXPIRED";
  pricePlanSnapshot: { finalPrice: string; currency: string };
};

export type MainTab = "home" | "messages" | "cart" | "profile";
