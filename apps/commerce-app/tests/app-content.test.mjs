import assert from "node:assert/strict";
import test from "node:test";

test("P08-S01 已替换 P01 静态占位页", async () => {
  const fs = await import("node:fs/promises");
  const sources = [
    "../App.tsx",
    "../src/config/runtime.ts",
    "../src/screens/LoginScreen.tsx",
    "../src/screens/ProductListScreen.tsx",
    "../src/screens/ProductDetailScreen.tsx",
  ];
  const source = (
    await Promise.all(
      sources.map((path) =>
        fs.readFile(new URL(path, import.meta.url), "utf8"),
      ),
    )
  ).join("\n");
  assert.match(source, /登录演示商城/);
  assert.match(source, /商品列表/);
  assert.match(source, /商品详情/);
  assert.match(source, /模拟数据/);
  assert.match(source, /http:\/\/127\.0\.0\.1:5173\/standalone/);
  assert.doesNotMatch(source, /暂无业务功能/);
});
