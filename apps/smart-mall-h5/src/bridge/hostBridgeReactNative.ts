import type { HostBridge, OpenProductInput } from "./hostBridge";

const BRIDGE_PROTOCOL = "hengpick.host-bridge" as const;
const BRIDGE_VERSION = "1.0" as const;

type BootstrapMessage = {
  protocol: typeof BRIDGE_PROTOCOL;
  version: typeof BRIDGE_VERSION;
  messageId: string;
  kind: "event";
  action: "bridge.bootstrap";
  timestamp: number;
  payload: {
    smartMallTicket: string;
    theme: "light" | "dark" | "system";
    locale: string;
  };
};

type Snapshot = {
  status: "idle" | "handshaking" | "initialized" | "standalone" | "error";
  accessToken?: string;
  accessTokenExpiresAt?: string;
  error?: string;
};

type Options = {
  postMessage: (raw: string) => void;
  exchangeTicket: (ticket: string) => Promise<{
    accessToken: string;
    accessTokenExpiresAt: string;
  }>;
  now?: () => number;
  createMessageId?: () => string;
  handshakeTimeoutMs?: number;
  onSnapshot?: (snapshot: Snapshot) => void;
};

function isBootstrap(value: unknown): value is BootstrapMessage {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const message = value as Record<string, unknown>;
  if (
    Object.keys(message).length !== 7 ||
    message.protocol !== BRIDGE_PROTOCOL ||
    message.version !== BRIDGE_VERSION ||
    message.kind !== "event" ||
    message.action !== "bridge.bootstrap" ||
    typeof message.messageId !== "string" ||
    !/^[0-7][0-9A-HJKMNP-TV-Z]{25}$/.test(message.messageId) ||
    typeof message.timestamp !== "number"
  )
    return false;
  const payload = message.payload;
  if (!payload || typeof payload !== "object" || Array.isArray(payload))
    return false;
  const fields = payload as Record<string, unknown>;
  return (
    Object.keys(fields).length === 3 &&
    typeof fields.smartMallTicket === "string" &&
    fields.smartMallTicket.length >= 16 &&
    ["light", "dark", "system"].includes(String(fields.theme)) &&
    typeof fields.locale === "string"
  );
}

function defaultMessageId() {
  const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  let value = alphabet[Math.floor(Math.random() * 8)];
  while (value.length < 26)
    value += alphabet[Math.floor(Math.random() * alphabet.length)];
  return value;
}

export function createHostBridgeReactNative({
  postMessage,
  exchangeTicket,
  now = Date.now,
  createMessageId = defaultMessageId,
  handshakeTimeoutMs = 500,
  onSnapshot,
}: Options): HostBridge & {
  start(): void;
  stop(): void;
  receive(raw: string): Promise<void>;
  getSnapshot(): Snapshot;
} {
  let snapshot: Snapshot = { status: "idle" };
  let timeout: ReturnType<typeof setTimeout> | undefined;
  const pending = new Map<
    string,
    {
      resolve: () => void;
      reject: (error: Error) => void;
      timeout: ReturnType<typeof setTimeout>;
    }
  >();

  function update(next: Snapshot) {
    snapshot = next;
    onSnapshot?.(snapshot);
  }

  function send(message: unknown) {
    postMessage(JSON.stringify(message));
  }

  return {
    mode: "react-native",
    capabilities: new Set(["openProduct"]),
    start() {
      update({ status: "handshaking" });
      send({
        protocol: BRIDGE_PROTOCOL,
        version: BRIDGE_VERSION,
        messageId: createMessageId(),
        kind: "event",
        action: "bridge.ready",
        timestamp: now(),
        payload: {
          supportedVersions: [BRIDGE_VERSION],
          capabilities: ["openProduct"],
        },
      });
      timeout = setTimeout(() => {
        if (snapshot.status === "handshaking") update({ status: "standalone" });
      }, handshakeTimeoutMs);
    },
    stop() {
      if (timeout) clearTimeout(timeout);
      pending.forEach(({ reject, timeout: requestTimeout }) => {
        clearTimeout(requestTimeout);
        reject(new Error("Bridge 已停止"));
      });
      pending.clear();
    },
    async receive(raw) {
      let message: unknown;
      try {
        message = JSON.parse(raw);
      } catch {
        return;
      }
      if (isBootstrap(message)) {
        if (Math.abs(now() - message.timestamp) > 5_000) {
          update({ status: "error", error: "Bootstrap 消息已过期" });
          return;
        }
        if (timeout) clearTimeout(timeout);
        try {
          const session = await exchangeTicket(message.payload.smartMallTicket);
          update({
            status: "initialized",
            accessToken: session.accessToken,
            accessTokenExpiresAt: session.accessTokenExpiresAt,
          });
        } catch (error) {
          update({
            status: "error",
            error: error instanceof Error ? error.message : "Ticket 兑换失败",
          });
        }
        return;
      }
      if (
        message &&
        typeof message === "object" &&
        !Array.isArray(message) &&
        (message as Record<string, unknown>).action === "openProduct" &&
        (message as Record<string, unknown>).kind === "response"
      ) {
        const reply = message as Record<string, unknown>;
        const waiter = pending.get(String(reply.replyTo));
        if (!waiter) return;
        clearTimeout(waiter.timeout);
        pending.delete(String(reply.replyTo));
        if (reply.success === true) waiter.resolve();
        else waiter.reject(new Error("宿主无法打开商品"));
      }
    },
    getSnapshot() {
      return snapshot;
    },
    openProduct({ productId, skuId }: OpenProductInput) {
      const requestId = createMessageId();
      return new Promise<void>((resolve, reject) => {
        const requestTimeout = setTimeout(() => {
          pending.delete(requestId);
          reject(new Error("打开商品请求超时"));
        }, 3_000);
        pending.set(requestId, { resolve, reject, timeout: requestTimeout });
        send({
          protocol: BRIDGE_PROTOCOL,
          version: BRIDGE_VERSION,
          messageId: requestId,
          kind: "request",
          action: "openProduct",
          timestamp: now(),
          payload: { productId, skuId },
        });
      });
    },
    async closeSmartMall() {
      throw new Error("当前 Bridge 版本不支持关闭宿主商城");
    },
  };
}
