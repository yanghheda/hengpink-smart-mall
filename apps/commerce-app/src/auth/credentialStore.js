export const REFRESH_TOKEN_STORAGE_KEY = "hengpick.auth.refresh-token";

export function createMemoryCredentialStore(secureStorage) {
  let accessToken = null;

  return {
    getAccessToken() {
      return accessToken;
    },
    getRefreshToken() {
      return secureStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
    },
    async save(tokens) {
      accessToken = tokens.accessToken;
      await secureStorage.setItem(
        REFRESH_TOKEN_STORAGE_KEY,
        tokens.refreshToken,
      );
    },
    async clear() {
      accessToken = null;
      await secureStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    },
  };
}
