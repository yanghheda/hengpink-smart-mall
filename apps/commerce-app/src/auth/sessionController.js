export function createSessionController({
  credentialStore,
  loginSession,
  refreshSession,
}) {
  return {
    async restore() {
      const refreshToken = await credentialStore.getRefreshToken();
      if (!refreshToken) return { status: "anonymous" };
      try {
        const tokens = await refreshSession(refreshToken);
        await credentialStore.save(tokens);
        return { status: "authenticated" };
      } catch (error) {
        await credentialStore.clear();
        return {
          status: "anonymous",
          reason: error?.status === 401 ? "expired" : "restore_failed",
        };
      }
    },
    async login(credentials) {
      const tokens = await loginSession(credentials);
      await credentialStore.save(tokens);
      return { status: "authenticated" };
    },
  };
}
