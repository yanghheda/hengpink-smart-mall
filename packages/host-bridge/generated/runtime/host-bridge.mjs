// Generated from schemas/bridge-message.schema.json. Do not edit.
const schema = {"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"https://hengpick.example/schemas/host-bridge/1.0/bridge-message.schema.json","title":"HengPick HostBridge P01-S03 Message","oneOf":[{"$ref":"#/$defs/readyMessage"},{"$ref":"#/$defs/bootstrapMessage"},{"$ref":"#/$defs/openProductMessage"},{"$ref":"#/$defs/openProductResponseMessage"},{"$ref":"#/$defs/openMockCheckoutMessage"},{"$ref":"#/$defs/openMockCheckoutResponseMessage"}],"$defs":{"protocol":{"const":"hengpick.host-bridge"},"version":{"type":"string","const":"1.0"},"messageId":{"type":"string","pattern":"^[0-7][0-9A-HJKMNP-TV-Z]{25}$"},"timestamp":{"type":"integer","minimum":0},"action":{"enum":["bridge.ready","bridge.bootstrap","openProduct","openMockCheckout"]},"productId":{"type":"string","minLength":1,"maxLength":64},"skuId":{"type":"string","minLength":1,"maxLength":64},"readyMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"event"},"action":{"const":"bridge.ready"},"timestamp":{"$ref":"#/$defs/timestamp"},"payload":{"type":"object","additionalProperties":false,"required":["supportedVersions","capabilities"],"properties":{"supportedVersions":{"type":"array","minItems":1,"uniqueItems":true,"items":{"$ref":"#/$defs/version"}},"capabilities":{"type":"array","uniqueItems":true,"items":{"enum":["openProduct","openMockCheckout"]}}}}}},"bootstrapMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"event"},"action":{"const":"bridge.bootstrap"},"timestamp":{"$ref":"#/$defs/timestamp"},"payload":{"type":"object","additionalProperties":false,"required":["smartMallTicket","theme","locale"],"properties":{"smartMallTicket":{"type":"string","minLength":16,"maxLength":4096},"theme":{"enum":["light","dark","system"]},"locale":{"type":"string","minLength":2,"maxLength":35,"pattern":"^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$"}}}}},"openProductMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"request"},"action":{"const":"openProduct"},"timestamp":{"$ref":"#/$defs/timestamp"},"payload":{"type":"object","additionalProperties":false,"required":["productId","skuId"],"properties":{"productId":{"$ref":"#/$defs/productId"},"skuId":{"$ref":"#/$defs/skuId"}}}}},"openMockCheckoutMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"request"},"action":{"const":"openMockCheckout"},"timestamp":{"$ref":"#/$defs/timestamp"},"payload":{"type":"object","additionalProperties":false,"required":["purchaseIntentId"],"properties":{"purchaseIntentId":{"type":"string","minLength":1,"maxLength":64}}}}},"openMockCheckoutResponseMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","replyTo","success","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"response"},"action":{"const":"openMockCheckout"},"timestamp":{"$ref":"#/$defs/timestamp"},"replyTo":{"$ref":"#/$defs/messageId"},"success":{"type":"boolean"},"payload":{"type":"object","additionalProperties":false,"properties":{}},"error":{"$ref":"#/$defs/bridgeError"}},"oneOf":[{"properties":{"success":{"const":true}},"not":{"required":["error"]}},{"required":["error"],"properties":{"success":{"const":false}}}]},"bridgeError":{"type":"object","additionalProperties":false,"required":["code","message"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{2,63}$"},"message":{"type":"string","minLength":1,"maxLength":256}}},"openProductResponseMessage":{"type":"object","additionalProperties":false,"required":["protocol","version","messageId","kind","action","timestamp","replyTo","success","payload"],"properties":{"protocol":{"$ref":"#/$defs/protocol"},"version":{"$ref":"#/$defs/version"},"messageId":{"$ref":"#/$defs/messageId"},"kind":{"const":"response"},"action":{"const":"openProduct"},"timestamp":{"$ref":"#/$defs/timestamp"},"replyTo":{"$ref":"#/$defs/messageId"},"success":{"type":"boolean"},"payload":{"type":"object","additionalProperties":false,"properties":{}},"error":{"type":"object","additionalProperties":false,"required":["code","message"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{2,63}$"},"message":{"type":"string","minLength":1,"maxLength":256}}}},"oneOf":[{"properties":{"success":{"const":true}},"not":{"required":["error"]}},{"required":["error"],"properties":{"success":{"const":false}}}]}}};
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
