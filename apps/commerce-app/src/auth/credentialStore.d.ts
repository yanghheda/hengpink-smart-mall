export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface SecureStoragePort {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

export interface CredentialStore {
  getAccessToken(): string | null;
  getRefreshToken(): Promise<string | null>;
  save(tokens: AuthTokens): Promise<void>;
  clear(): Promise<void>;
}

export const REFRESH_TOKEN_STORAGE_KEY: string;
export function createMemoryCredentialStore(
  secureStorage: SecureStoragePort,
): CredentialStore;
