import assert from "node:assert/strict";
import test from "node:test";

import { ApiError, createApiClient } from "../src/api/client.js";

test("受保护请求遇到 401 时触发统一会话过期处理", async () => {
  let expiredCalls = 0;
  const client = createApiClient({
    baseUrl: "http://api.test",
    getAccessToken: () => "expired-access",
    onSessionExpired: async () => {
      expiredCalls += 1;
    },
    fetchImpl: async () =>
      new Response(
        JSON.stringify({
          error: { code: "AUTH_TOKEN_INVALID", message: "登录已过期" },
        }),
        {
          status: 401,
          headers: { "content-type": "application/json" },
        },
      ),
  });

  await assert.rejects(client.get("/api/v1/products"), ApiError);
  assert.equal(expiredCalls, 1);
});

test("商品详情 404 保留稳定错误码供不存在页面判断", async () => {
  const client = createApiClient({
    baseUrl: "http://api.test",
    getAccessToken: () => "access-1",
    fetchImpl: async () =>
      new Response(
        JSON.stringify({
          error: { code: "PRODUCT_NOT_FOUND", message: "商品不存在" },
        }),
        {
          status: 404,
          headers: { "content-type": "application/json" },
        },
      ),
  });

  await assert.rejects(
    client.get("/api/v1/products/missing"),
    (error) =>
      error instanceof ApiError &&
      error.status === 404 &&
      error.code === "PRODUCT_NOT_FOUND",
  );
});

test("金额字段保持服务端字符串，不在客户端转换为浮点数", async () => {
  const client = createApiClient({
    baseUrl: "http://api.test",
    fetchImpl: async () =>
      new Response(JSON.stringify({ data: { minPrice: "2699.00" } }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
  });

  const data = await client.get("/api/v1/products");

  assert.equal(data.minPrice, "2699.00");
  assert.equal(typeof data.minPrice, "string");
});
