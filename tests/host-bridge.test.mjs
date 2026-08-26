import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";

import { reactNativeBridgeValidator } from "../packages/host-bridge/src/react-native-validator.mjs";
import { webBridgeValidator } from "../packages/host-bridge/src/web-validator.mjs";
import {
  generateArtifacts,
  validateBridgeSchema,
} from "../packages/host-bridge/scripts/bridge-tool.mjs";

const schemaPath = "packages/host-bridge/schemas/bridge-message.schema.json";
const ulid = "01J00000000000000000000000";

const messages = [
  {
    name: "ready event",
    valid: true,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "event",
      action: "bridge.ready",
      timestamp: 1787596800000,
      payload: {
        supportedVersions: ["1.0"],
        capabilities: ["openProduct"],
      },
    },
  },
  {
    name: "bootstrap event",
    valid: true,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "event",
      action: "bridge.bootstrap",
      timestamp: 1787596800000,
      payload: {
        smartMallTicket: "opaque-ticket-value-with-enough-entropy",
        theme: "light",
        locale: "zh-CN",
      },
    },
  },
  {
    name: "openProduct request",
    valid: true,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "request",
      action: "openProduct",
      timestamp: 1787596800000,
      payload: { productId: "P1001", skuId: "SKU1001-256-BLACK" },
    },
  },
  {
    name: "openProduct success response",
    valid: true,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "response",
      action: "openProduct",
      timestamp: 1787596800000,
      replyTo: ulid,
      success: true,
      payload: {},
    },
  },
  {
    name: "failed response requires a safe error",
    valid: false,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "response",
      action: "openProduct",
      timestamp: 1787596800000,
      replyTo: ulid,
      success: false,
      payload: {},
    },
  },
  {
    name: "openProduct safe error response",
    valid: true,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "response",
      action: "openProduct",
      timestamp: 1787596800000,
      replyTo: ulid,
      success: false,
      payload: {},
      error: {
        code: "BRIDGE_NAVIGATION_FAILED",
        message: "Product detail is temporarily unavailable",
      },
    },
  },
  {
    name: "unknown action",
    valid: false,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "request",
      action: "openMockCheckout",
      timestamp: 1787596800000,
      payload: {},
    },
  },
  {
    name: "openProduct cannot smuggle price",
    valid: false,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: ulid,
      kind: "request",
      action: "openProduct",
      timestamp: 1787596800000,
      payload: {
        productId: "P1001",
        skuId: "SKU1001-256-BLACK",
        price: "2699.00",
      },
    },
  },
  {
    name: "invalid message id",
    valid: false,
    value: {
      protocol: "hengpick.host-bridge",
      version: "1.0",
      messageId: "not-a-ulid",
      kind: "event",
      action: "bridge.ready",
      timestamp: 1787596800000,
      payload: { supportedVersions: ["1.0"], capabilities: [] },
    },
  },
];

test("Bridge schema freezes only ready, bootstrap and openProduct", async () => {
  const schema = JSON.parse(await readFile(schemaPath, "utf8"));

  assert.doesNotThrow(() => validateBridgeSchema(schema));
  assert.deepEqual(schema.$defs.action.enum, [
    "bridge.ready",
    "bridge.bootstrap",
    "openProduct",
  ]);
});

test("RN and H5 accept and reject the same Bridge messages", () => {
  for (const message of messages) {
    const rn = reactNativeBridgeValidator(message.value);
    const web = webBridgeValidator(message.value);

    assert.equal(rn.ok, message.valid, `RN mismatch: ${message.name}`);
    assert.equal(web.ok, message.valid, `H5 mismatch: ${message.name}`);
    assert.deepEqual(web, rn, `cross-client drift: ${message.name}`);
  }
});

test("generated Bridge runtime and types are byte-for-byte current", async () => {
  const schema = JSON.parse(await readFile(schemaPath, "utf8"));

  for (const artifact of generateArtifacts(schema)) {
    assert.equal(
      await readFile(artifact.path, "utf8"),
      artifact.content,
      `${artifact.path} is stale`,
    );
  }
});

test("Bridge check catches schema edits before generated clients drift", async () => {
  const schema = JSON.parse(await readFile(schemaPath, "utf8"));
  schema.$defs.productId.maxLength = 127;

  const directory = await mkdtemp(join(tmpdir(), "hengpick-bridge-"));
  const changedSchema = join(directory, "bridge-message.schema.json");
  await writeFile(
    changedSchema,
    `${JSON.stringify(schema, null, 2)}\n`,
    "utf8",
  );

  const result = spawnSync(
    process.execPath,
    [
      "packages/host-bridge/scripts/bridge-tool.mjs",
      "check",
      "--spec",
      changedSchema,
    ],
    { encoding: "utf8" },
  );

  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}${result.stderr}`, /generated Bridge drift/i);
});
