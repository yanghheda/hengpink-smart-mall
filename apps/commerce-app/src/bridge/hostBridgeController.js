import { validateBridgeMessage } from "../../../../packages/host-bridge/generated/runtime/host-bridge.mjs";

const BRIDGE_PROTOCOL = "hengpick.host-bridge";
const BRIDGE_VERSION = "1.0";
const MESSAGE_MAX_AGE_MS = 5_000;

function messageId() {
  const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  let value = alphabet[Math.floor(Math.random() * 8)];
  while (value.length < 26)
    value += alphabet[Math.floor(Math.random() * alphabet.length)];
  return value;
}

export function createHostBridgeController({
  allowedOrigin,
  now = Date.now,
  createTicket,
  send,
  openProduct,
  theme = "light",
  locale = "zh-CN",
}) {
  const handledResponses = new Map();

  function validContext(message, origin) {
    if (origin !== allowedOrigin) return "BRIDGE_ORIGIN_REJECTED";
    if (Math.abs(now() - message.timestamp) > MESSAGE_MAX_AGE_MS)
      return "BRIDGE_MESSAGE_EXPIRED";
    return null;
  }

  function responseFor(request, success, error) {
    return {
      protocol: BRIDGE_PROTOCOL,
      version: BRIDGE_VERSION,
      messageId: messageId(),
      kind: "response",
      action: "openProduct",
      timestamp: now(),
      replyTo: request.messageId,
      success,
      payload: {},
      ...(error ? { error } : {}),
    };
  }

  return {
    async onMessage(raw, origin) {
      let message;
      try {
        message = JSON.parse(raw);
      } catch {
        return { ok: false, error: "BRIDGE_MESSAGE_INVALID" };
      }
      const validation = validateBridgeMessage(message);
      if (!validation.ok) return { ok: false, error: "BRIDGE_MESSAGE_INVALID" };
      const contextError = validContext(message, origin);
      if (contextError) return { ok: false, error: contextError };

      if (message.action === "bridge.ready") {
        if (!message.payload.supportedVersions.includes(BRIDGE_VERSION))
          return { ok: false, error: "BRIDGE_VERSION_INCOMPATIBLE" };
        const { ticket } = await createTicket();
        send({
          protocol: BRIDGE_PROTOCOL,
          version: BRIDGE_VERSION,
          messageId: messageId(),
          kind: "event",
          action: "bridge.bootstrap",
          timestamp: now(),
          payload: { smartMallTicket: ticket, theme, locale },
        });
        return { ok: true };
      }

      if (message.action === "openProduct" && message.kind === "request") {
        const cached = handledResponses.get(message.messageId);
        if (cached) {
          send(cached);
          return { ok: true, duplicate: true };
        }
        try {
          openProduct({
            productId: message.payload.productId,
            skuId: message.payload.skuId,
          });
          const response = responseFor(message, true);
          handledResponses.set(message.messageId, response);
          send(response);
          return { ok: true };
        } catch {
          const response = responseFor(message, false, {
            code: "OPEN_PRODUCT_FAILED",
            message: "商品详情暂时无法打开",
          });
          handledResponses.set(message.messageId, response);
          send(response);
          return { ok: false, error: "OPEN_PRODUCT_FAILED" };
        }
      }
      return { ok: false, error: "BRIDGE_ACTION_UNSUPPORTED" };
    },
  };
}
