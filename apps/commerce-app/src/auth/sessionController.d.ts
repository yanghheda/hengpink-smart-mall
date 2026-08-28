import type { AuthTokens, CredentialStore } from "./credentialStore.js";
export type SessionResult = {
  status: "anonymous" | "authenticated";
  reason?: "expired" | "restore_failed";
};
export function createSessionController(options: {
  credentialStore: CredentialStore;
  loginSession?: (credentials: Record<string, string>) => Promise<AuthTokens>;
  refreshSession?: (refreshToken: string) => Promise<AuthTokens>;
}): {
  restore(): Promise<SessionResult>;
  login(credentials: Record<string, string>): Promise<SessionResult>;
};
