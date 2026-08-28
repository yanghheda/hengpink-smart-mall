import assert from "node:assert/strict";
import test from "node:test";

import {
  createMemoryCredentialStore,
  REFRESH_TOKEN_STORAGE_KEY,
} from "../src/auth/credentialStore.js";

test("凭证存储只在内存保留 Access Token，并通过安全存储端口保存 Refresh Token", async () => {
  const writes = [];
  const secureStorage = {
    getItem: async () => "stored-refresh",
    setItem: async (key, value) => writes.push([key, value]),
    removeItem: async (key) => writes.push([key, null]),
  };
  const store = createMemoryCredentialStore(secureStorage);

  await store.save({ accessToken: "access-1", refreshToken: "refresh-1" });

  assert.equal(store.getAccessToken(), "access-1");
  assert.deepEqual(writes, [[REFRESH_TOKEN_STORAGE_KEY, "refresh-1"]]);
  assert.equal(await store.getRefreshToken(), "stored-refresh");

  await store.clear();
  assert.equal(store.getAccessToken(), null);
  assert.deepEqual(writes.at(-1), [REFRESH_TOKEN_STORAGE_KEY, null]);
});
