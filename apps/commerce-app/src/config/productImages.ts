const categoryImages: Record<string, string> = {
  PHONE:
    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=1000&q=85",
  MONITOR:
    "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=1000&q=85",
  HEADPHONE:
    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1000&q=85",
  AIR_PURIFIER:
    "https://images.unsplash.com/photo-1585771724684-38269d6639fd?auto=format&fit=crop&w=1000&q=85",
  OFFICE_CHAIR:
    "https://images.unsplash.com/photo-1580480055273-228ff5388ef8?auto=format&fit=crop&w=1000&q=85",
};

const productImages: Record<string, string> = {
  "P-PIXEL-9A":
    "https://images.unsplash.com/photo-1598327105666-5b89351aff97?auto=format&fit=crop&w=1000&q=85",
  "P-LUMEN-14":
    "https://images.unsplash.com/photo-1601784551446-20c9e07cdbdb?auto=format&fit=crop&w=1000&q=85",
  "P-ORBIT-X5":
    "https://images.unsplash.com/photo-1605236453806-6ff36851218e?auto=format&fit=crop&w=1000&q=85",
  "P-SONORA-BUDS":
    "https://images.unsplash.com/photo-1606220945770-b5b6c2c55bf1?auto=format&fit=crop&w=1000&q=85",
};

export function getProductImage(productId: string, categoryId: string) {
  return productImages[productId] ?? categoryImages[categoryId];
}
