import assert from "node:assert/strict";
import test from "node:test";

import { createSessionController } from "../src/auth/sessionController.js";

function createCredentialStore(refreshToken = null) {
  let accessToken = null;
  let storedRefreshToken = refreshToken;
  return {
    getAccessToken: () => accessToken,
    getRefreshToken: async () => storedRefreshToken,
    save: async (tokens) => {
      accessToken = tokens.accessToken;
      storedRefreshToken = tokens.refreshToken;
    },
    clear: async () => {
      accessToken = null;
      storedRefreshToken = null;
    },
  };
}

test("冷启动无 Refresh Token 时保持未登录且不调用刷新接口", async () => {
  let refreshCalls = 0;
  const controller = createSessionController({
    credentialStore: createCredentialStore(),
    refreshSession: async () => {
      refreshCalls += 1;
    },
  });

  const result = await controller.restore();

  assert.equal(result.status, "anonymous");
  assert.equal(refreshCalls, 0);
});

test("冷启动 Refresh Token 过期时清理凭证并明确返回过期原因", async () => {
  const credentialStore = createCredentialStore("expired-refresh");
  const controller = createSessionController({
    credentialStore,
    refreshSession: async () => {
      const error = new Error("Refresh Token 已过期");
      error.status = 401;
      throw error;
    },
  });

  const result = await controller.restore();

  assert.deepEqual(result, { status: "anonymous", reason: "expired" });
  assert.equal(await credentialStore.getRefreshToken(), null);
});

test("登录成功后只通过凭证端口保存服务端签发的 Token", async () => {
  const credentialStore = createCredentialStore();
  const controller = createSessionController({
    credentialStore,
    loginSession: async () => ({
      accessToken: "access-1",
      refreshToken: "refresh-1",
    }),
  });

  const result = await controller.login({
    account: "demo_user",
    password: "demo_password",
    deviceSessionId: "device-1",
  });

  assert.equal(result.status, "authenticated");
  assert.equal(credentialStore.getAccessToken(), "access-1");
  assert.equal(await credentialStore.getRefreshToken(), "refresh-1");
});
