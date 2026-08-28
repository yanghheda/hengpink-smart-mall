import assert from "node:assert/strict";
import test from "node:test";

import { createNavigationStore } from "../src/navigation/navigationStore.js";

test("Zustand 导航状态只保存资源标识，不复制商品服务端事实", () => {
  const store = createNavigationStore();

  store
    .getState()
    .openProduct({ productId: "P-1", skuId: "S-1", displayName: "不应缓存" });

  assert.deepEqual(store.getState().selectedProduct, {
    productId: "P-1",
    skuId: "S-1",
  });
  assert.equal("displayName" in store.getState().selectedProduct, false);
});
