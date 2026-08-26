// Generated from schemas/bridge-message.schema.json. Do not edit.

export const BRIDGE_PROTOCOL = "hengpick.host-bridge" as const;
export const BRIDGE_VERSION = "1.0" as const;

export type BridgeCapability = "openProduct";

interface BridgeEnvelope {
  protocol: typeof BRIDGE_PROTOCOL;
  version: typeof BRIDGE_VERSION;
  messageId: string;
  timestamp: number;
}

export interface BridgeReadyMessage extends BridgeEnvelope {
  kind: "event";
  action: "bridge.ready";
  payload: {
    supportedVersions: Array<typeof BRIDGE_VERSION>;
    capabilities: BridgeCapability[];
  };
}

export interface BridgeBootstrapMessage extends BridgeEnvelope {
  kind: "event";
  action: "bridge.bootstrap";
  payload: {
    smartMallTicket: string;
    theme: "light" | "dark" | "system";
    locale: string;
  };
}

export interface OpenProductMessage extends BridgeEnvelope {
  kind: "request";
  action: "openProduct";
  payload: {
    productId: string;
    skuId: string;
  };
}

export interface OpenProductResponseMessage extends BridgeEnvelope {
  kind: "response";
  action: "openProduct";
  replyTo: string;
  success: boolean;
  payload: Record<string, never>;
  error?: {
    code: string;
    message: string;
  };
}

export type BridgeMessage =
  | BridgeReadyMessage
  | BridgeBootstrapMessage
  | OpenProductMessage
  | OpenProductResponseMessage;
