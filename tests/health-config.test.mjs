import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("P02-S03 keeps client health metadata public and free of credentials", async () => {
  const health = JSON.parse(
    await readFile("apps/smart-mall-h5/public/health.json", "utf8"),
  );

  assert.deepEqual(Object.keys(health).sort(), [
    "apiContractVersion",
    "bridgeMajor",
    "builtAt",
    "gitSha",
    "scope",
    "service",
    "status",
  ]);
  assert.equal(health.status, "UP");
  assert.equal(health.bridgeMajor, 1);
  assert.doesNotMatch(JSON.stringify(health), /key|token|password|secret/i);
});

test("P02-S03 documents explicit service configuration without real secrets", async () => {
  const environment = await readFile(".env.example", "utf8");

  for (const name of [
    "APP_ENV",
    "MYSQL_URL",
    "REDIS_URL",
    "QDRANT_URL",
    "AGENT_TOOL_API_BASE_URL",
    "AGENT_MODEL_API_KEY",
  ]) {
    assert.match(environment, new RegExp(`^${name}=`, "m"));
  }
  assert.doesNotMatch(environment, /yang1998|123456/);
});
