import type { BridgeMessage } from "../typescript/host-bridge";

export type BridgeValidationResult =
  | { ok: true; value: BridgeMessage }
  | { ok: false; errors: string[] };

export function validateBridgeMessage(value: unknown): BridgeValidationResult;
