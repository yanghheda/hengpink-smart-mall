import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { pathToFileURL } from "node:url";

const defaultSchemaPath =
  "packages/host-bridge/schemas/bridge-message.schema.json";
const actions = [
  "bridge.ready",
  "bridge.bootstrap",
  "openProduct",
  "openMockCheckout",
];

function requiredObject(value, label) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  return value;
}

export function validateBridgeSchema(schema) {
  requiredObject(schema, "Bridge schema");
  if (schema.$schema !== "https://json-schema.org/draft/2020-12/schema") {
    throw new Error("P01-S03 requires JSON Schema draft 2020-12");
  }
  if (!schema.$id?.includes("/host-bridge/1.0/")) {
    throw new Error("Bridge schema must have a versioned 1.0 $id");
  }

  const definitions = requiredObject(schema.$defs, "Bridge schema $defs");
  if (JSON.stringify(definitions.action?.enum) !== JSON.stringify(actions)) {
    throw new Error(`Bridge actions must be exactly: ${actions.join(", ")}`);
  }

  const expectedMessages = [
    ["readyMessage", "event", "bridge.ready"],
    ["bootstrapMessage", "event", "bridge.bootstrap"],
    ["openProductMessage", "request", "openProduct"],
    ["openProductResponseMessage", "response", "openProduct"],
    ["openMockCheckoutMessage", "request", "openMockCheckout"],
    ["openMockCheckoutResponseMessage", "response", "openMockCheckout"],
  ];
  for (const [name, kind, action] of expectedMessages) {
    const message = requiredObject(definitions[name], `$defs.${name}`);
    if (message.additionalProperties !== false) {
      throw new Error(`$defs.${name} must reject additional properties`);
    }
    if (message.properties?.kind?.const !== kind) {
      throw new Error(`$defs.${name} kind must be ${kind}`);
    }
    if (message.properties?.action?.const !== action) {
      throw new Error(`$defs.${name} action must be ${action}`);
    }
    if (message.properties?.payload?.additionalProperties !== false) {
      throw new Error(
        `$defs.${name}.payload must reject additional properties`,
      );
    }
  }

  for (const name of ["productId", "skuId"]) {
    const definition = requiredObject(definitions[name], `$defs.${name}`);
    if (
      definition.type !== "string" ||
      !Number.isInteger(definition.minLength) ||
      !Number.isInteger(definition.maxLength) ||
      definition.minLength < 1 ||
      definition.maxLength < definition.minLength
    ) {
      throw new Error(`$defs.${name} must define a bounded non-empty string`);
    }
  }

  return schema;
}

function typescriptArtifact() {
  return `// Generated from schemas/bridge-message.schema.json. Do not edit.\n\nexport const BRIDGE_PROTOCOL = "hengpick.host-bridge" as const;\nexport const BRIDGE_VERSION = "1.0" as const;\n\nexport type BridgeCapability = "openProduct";\n\ninterface BridgeEnvelope {\n  protocol: typeof BRIDGE_PROTOCOL;\n  version: typeof BRIDGE_VERSION;\n  messageId: string;\n  timestamp: number;\n}\n\nexport interface BridgeReadyMessage extends BridgeEnvelope {\n  kind: "event";\n  action: "bridge.ready";\n  payload: {\n    supportedVersions: Array<typeof BRIDGE_VERSION>;\n    capabilities: BridgeCapability[];\n  };\n}\n\nexport interface BridgeBootstrapMessage extends BridgeEnvelope {\n  kind: "event";\n  action: "bridge.bootstrap";\n  payload: {\n    smartMallTicket: string;\n    theme: "light" | "dark" | "system";\n    locale: string;\n  };\n}\n\nexport interface OpenProductMessage extends BridgeEnvelope {\n  kind: "request";\n  action: "openProduct";\n  payload: {\n    productId: string;\n    skuId: string;\n  };\n}\n\nexport interface OpenProductResponseMessage extends BridgeEnvelope {\n  kind: "response";\n  action: "openProduct";\n  replyTo: string;\n  success: boolean;\n  payload: Record<string, never>;\n  error?: {\n    code: string;\n    message: string;\n  };\n}\n\nexport type BridgeMessage =\n  | BridgeReadyMessage\n  | BridgeBootstrapMessage\n  | OpenProductMessage\n  | OpenProductResponseMessage;\n`;
}

function runtimeArtifact(schema) {
  const serialized = JSON.stringify(schema);
  return `// Generated from schemas/bridge-message.schema.json. Do not edit.
const schema = ${serialized};
const definitions = schema.$defs;
const envelopeKeys = ["protocol", "version", "messageId", "kind", "action", "timestamp", "payload"];

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasExactKeys(value, keys) {
  return (
    isObject(value) &&
    Object.keys(value).length === keys.length &&
    keys.every((key) => Object.hasOwn(value, key))
  );
}

function matchesString(value, definition) {
  if (typeof value !== "string") return false;
  if (definition.const !== undefined && value !== definition.const) return false;
  if (definition.minLength !== undefined && value.length < definition.minLength) return false;
  if (definition.maxLength !== undefined && value.length > definition.maxLength) return false;
  return definition.pattern === undefined || new RegExp(definition.pattern).test(value);
}

function uniqueAllowedStrings(value, allowed, minimum = 0) {
  return (
    Array.isArray(value) &&
    value.length >= minimum &&
    new Set(value).size === value.length &&
    value.every((item) => typeof item === "string" && allowed.includes(item))
  );
}

function validateEnvelope(value, errors) {
  if (!isObject(value) || !envelopeKeys.every((key) => Object.hasOwn(value, key))) {
    errors.push("message must contain the required envelope fields");
    return false;
  }
  if (value.protocol !== definitions.protocol.const) errors.push("protocol is unsupported");
  if (value.version !== definitions.version.const) errors.push("version is unsupported");
  if (!matchesString(value.messageId, definitions.messageId)) errors.push("messageId must be a ULID");
  if (!Number.isInteger(value.timestamp) || value.timestamp < definitions.timestamp.minimum) errors.push("timestamp must be a non-negative integer");
  return errors.length === 0;
}

function validateReady(value, errors) {
  if (!hasExactKeys(value, envelopeKeys)) errors.push("bridge.ready envelope has invalid fields");
  if (value.kind !== "event") errors.push("bridge.ready kind must be event");
  const payload = value.payload;
  if (!hasExactKeys(payload, ["supportedVersions", "capabilities"])) {
    errors.push("bridge.ready payload has invalid fields");
    return;
  }
  if (!uniqueAllowedStrings(payload.supportedVersions, [definitions.version.const], 1)) errors.push("supportedVersions is invalid");
  if (!uniqueAllowedStrings(payload.capabilities, ["openProduct", "openMockCheckout"])) errors.push("capabilities is invalid");
}

function validateBootstrap(value, errors) {
  if (!hasExactKeys(value, envelopeKeys)) errors.push("bridge.bootstrap envelope has invalid fields");
  if (value.kind !== "event") errors.push("bridge.bootstrap kind must be event");
  const payload = value.payload;
  if (!hasExactKeys(payload, ["smartMallTicket", "theme", "locale"])) {
    errors.push("bridge.bootstrap payload has invalid fields");
    return;
  }
  const definition = definitions.bootstrapMessage.properties.payload.properties;
  if (!matchesString(payload.smartMallTicket, definition.smartMallTicket)) errors.push("smartMallTicket is invalid");
  if (!definition.theme.enum.includes(payload.theme)) errors.push("theme is invalid");
  if (!matchesString(payload.locale, definition.locale)) errors.push("locale is invalid");
}

function validateOpenProductResponse(value, errors) {
  const definition = definitions.openProductResponseMessage.properties;
  const keys = [...envelopeKeys, "replyTo", "success"];
  if (value.success === false) keys.push("error");
  if (!hasExactKeys(value, keys)) errors.push("openProduct response envelope has invalid fields");
  if (!matchesString(value.replyTo, definitions.messageId)) errors.push("replyTo must be a ULID");
  if (typeof value.success !== "boolean") errors.push("success must be boolean");
  if (!hasExactKeys(value.payload, [])) errors.push("openProduct response payload must be empty");
  if (value.success === false) {
    if (!hasExactKeys(value.error, ["code", "message"])) {
      errors.push("failed response requires code and message");
      return;
    }
    if (!matchesString(value.error.code, definition.error.properties.code)) errors.push("error.code is invalid");
    if (!matchesString(value.error.message, definition.error.properties.message)) errors.push("error.message is invalid");
  }
}

function validateOpenProduct(value, errors) {
  if (value.kind === "response") {
    validateOpenProductResponse(value, errors);
    return;
  }
  if (!hasExactKeys(value, envelopeKeys)) errors.push("openProduct request envelope has invalid fields");
  if (value.kind !== "request") errors.push("openProduct kind must be request or response");
  const payload = value.payload;
  if (!hasExactKeys(payload, ["productId", "skuId"])) {
    errors.push("openProduct payload has invalid fields");
    return;
  }
  if (!matchesString(payload.productId, definitions.productId)) errors.push("productId is invalid");
  if (!matchesString(payload.skuId, definitions.skuId)) errors.push("skuId is invalid");
}

function validateOpenMockCheckout(value, errors) {
  if (value.kind === "response") {
    const keys = [...envelopeKeys, "replyTo", "success"];
    if (value.success === false) keys.push("error");
    if (!hasExactKeys(value, keys)) errors.push("openMockCheckout response envelope has invalid fields");
    if (!matchesString(value.replyTo, definitions.messageId)) errors.push("replyTo must be a ULID");
    if (!hasExactKeys(value.payload, [])) errors.push("openMockCheckout response payload must be empty");
    return;
  }
  if (!hasExactKeys(value, envelopeKeys)) errors.push("openMockCheckout request envelope has invalid fields");
  const definition = definitions.openMockCheckoutMessage.properties.payload.properties.purchaseIntentId;
  if (value.kind !== "request" || !hasExactKeys(value.payload, ["purchaseIntentId"])
      || !matchesString(value.payload.purchaseIntentId, definition)) {
    errors.push("openMockCheckout payload is invalid");
  }
}

export function validateBridgeMessage(value) {
  const errors = [];
  if (!validateEnvelope(value, errors)) return { ok: false, errors };
  if (!definitions.action.enum.includes(value.action)) {
    return { ok: false, errors: ["action is unsupported"] };
  }
  if (value.action === "bridge.ready") validateReady(value, errors);
  if (value.action === "bridge.bootstrap") validateBootstrap(value, errors);
  if (value.action === "openProduct") validateOpenProduct(value, errors);
  if (value.action === "openMockCheckout") validateOpenMockCheckout(value, errors);
  return errors.length === 0 ? { ok: true, value } : { ok: false, errors };
}
`;
}

function declarationArtifact() {
  return `import type { BridgeMessage } from "../typescript/host-bridge";\n\nexport type BridgeValidationResult =\n  | { ok: true; value: BridgeMessage }\n  | { ok: false; errors: string[] };\n\nexport function validateBridgeMessage(value: unknown): BridgeValidationResult;\n`;
}

export function generateArtifacts(schema) {
  validateBridgeSchema(schema);
  return [
    {
      path: "packages/host-bridge/generated/typescript/host-bridge.ts",
      content: typescriptArtifact(),
    },
    {
      path: "packages/host-bridge/generated/runtime/host-bridge.mjs",
      content: runtimeArtifact(schema),
    },
    {
      path: "packages/host-bridge/generated/runtime/host-bridge.d.mts",
      content: declarationArtifact(),
    },
  ];
}

async function readSchema(path) {
  return JSON.parse(await readFile(path, "utf8"));
}

async function run(command, schemaPath) {
  const schema = await readSchema(schemaPath);
  const artifacts = generateArtifacts(schema);

  if (command === "validate") {
    console.log("HostBridge schema is valid.");
    return;
  }
  if (command === "generate") {
    await Promise.all(
      artifacts.map(async (artifact) => {
        await mkdir(dirname(artifact.path), { recursive: true });
        await writeFile(artifact.path, artifact.content);
      }),
    );
    console.log("Generated HostBridge runtime and TypeScript types.");
    return;
  }
  if (command === "check") {
    for (const artifact of artifacts) {
      let current;
      try {
        current = await readFile(artifact.path, "utf8");
      } catch {
        throw new Error(`generated Bridge drift: missing ${artifact.path}`);
      }
      if (current !== artifact.content) {
        throw new Error(`generated Bridge drift: ${artifact.path} is stale`);
      }
    }
    console.log(
      "HostBridge schema is valid and generated artifacts are current.",
    );
    return;
  }
  throw new Error(
    "Usage: bridge-tool.mjs <validate|generate|check> [--spec path]",
  );
}

const isEntrypoint = process.argv[1]
  ? import.meta.url === pathToFileURL(process.argv[1]).href
  : false;
if (isEntrypoint) {
  const command = process.argv[2];
  const specIndex = process.argv.indexOf("--spec");
  const schemaPath =
    specIndex === -1 ? defaultSchemaPath : process.argv[specIndex + 1];
  run(command, schemaPath).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
