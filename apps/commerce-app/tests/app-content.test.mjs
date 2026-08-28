import assert from "node:assert/strict";
import test from "node:test";

test("P08-S01 已替换 P01 静态占位页", async () => {
  const source = await import("node:fs/promises").then((fs) =>
    fs.readFile(new URL("../App.tsx", import.meta.url), "utf8"),
  );
  assert.match(source, /登录演示商城/);
  assert.match(source, /商品列表/);
  assert.match(source, /商品详情/);
  assert.match(source, /模拟数据/);
  assert.doesNotMatch(source, /暂无业务功能/);
});
