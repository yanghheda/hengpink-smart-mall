import assert from "node:assert/strict";
import test from "node:test";

import { createHostBridgeController } from "../src/bridge/hostBridgeController.js";

const READY_ID = "01J00000000000000000000000";
const OPEN_ID = "01J00000000000000000000001";

function ready(overrides = {}) {
  return JSON.stringify({
    protocol: "hengpick.host-bridge",
    version: "1.0",
    messageId: READY_ID,
    kind: "event",
    action: "bridge.ready",
    timestamp: 1_000,
    payload: { supportedVersions: ["1.0"], capabilities: ["openProduct"] },
    ...overrides,
  });
}

function openProduct(overrides = {}) {
  return JSON.stringify({
    protocol: "hengpick.host-bridge",
    version: "1.0",
    messageId: OPEN_ID,
    kind: "request",
    action: "openProduct",
    timestamp: 1_000,
    payload: { productId: "product-1", skuId: "sku-1" },
    ...overrides,
  });
}

test("ready 通过版本与来源校验后才发送不含 URL 凭证的 bootstrap", async () => {
  const sent = [];
  const controller = createHostBridgeController({
    allowedOrigin: "https://smart.example",
    now: () => 1_000,
    createTicket: async () => ({ ticket: "opaque-ticket-value" }),
    send: (message) => sent.push(message),
    openProduct: () => assert.fail("不应导航"),
  });

  await controller.onMessage(ready(), "https://smart.example");

  assert.equal(sent.length, 1);
  assert.equal(sent[0].action, "bridge.bootstrap");
  assert.equal(sent[0].payload.smartMallTicket, "opaque-ticket-value");
  assert.equal(JSON.stringify(sent[0]).includes("accessToken"), false);
});

test("拒绝错误来源、过期消息和不兼容 Major，且不申请 Ticket", async () => {
  let ticketCalls = 0;
  const controller = createHostBridgeController({
    allowedOrigin: "https://smart.example",
    now: () => 20_000,
    createTicket: async () => {
      ticketCalls += 1;
      return { ticket: "opaque-ticket-value" };
    },
    send: () => assert.fail("不应发送"),
    openProduct: () => assert.fail("不应导航"),
  });

  assert.equal(
    (await controller.onMessage(ready(), "https://evil.example")).ok,
    false,
  );
  assert.equal(
    (await controller.onMessage(ready(), "https://smart.example")).ok,
    false,
  );
  assert.equal(
    (
      await controller.onMessage(
        ready({ version: "2.0" }),
        "https://smart.example",
      )
    ).ok,
    false,
  );
  assert.equal(ticketCalls, 0);
});

test("openProduct 忽略重复 messageId，只用 ID 导航并返回成功", async () => {
  const selections = [];
  const replies = [];
  const controller = createHostBridgeController({
    allowedOrigin: "https://smart.example",
    now: () => 1_000,
    createTicket: async () => ({ ticket: "opaque-ticket-value" }),
    send: (message) => replies.push(message),
    openProduct: (selection) => selections.push(selection),
  });

  await controller.onMessage(openProduct(), "https://smart.example");
  await controller.onMessage(openProduct(), "https://smart.example");

  assert.deepEqual(selections, [{ productId: "product-1", skuId: "sku-1" }]);
  assert.equal(replies.length, 2);
  assert.equal(replies[0].replyTo, OPEN_ID);
  assert.equal(replies[0].success, true);
});

test("带 price 的篡改请求因 Schema 额外字段被拒绝", async () => {
  const controller = createHostBridgeController({
    allowedOrigin: "https://smart.example",
    now: () => 1_000,
    createTicket: async () => ({ ticket: "opaque-ticket-value" }),
    send: () => assert.fail("非法请求不应响应"),
    openProduct: () => assert.fail("非法请求不应导航"),
  });

  const result = await controller.onMessage(
    openProduct({
      payload: { productId: "product-1", skuId: "sku-1", price: "0.01" },
    }),
    "https://smart.example",
  );

  assert.equal(result.ok, false);
});
