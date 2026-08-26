import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));

test("root workspace exposes the four P01-S01 applications", async () => {
  const root = await readJson("package.json");

  assert.deepEqual(root.workspaces, [
    "apps/commerce-app",
    "apps/smart-mall-h5",
  ]);
  assert.equal(
    root.scripts.test,
    "node --test tests/*.test.mjs && npm run test:js",
  );
  assert.equal(
    root.scripts.check,
    "npm run contract:check && npm run bridge:check && npm run tokens:check && npm run format:check && npm run build:js && npm test && npm run test:services",
  );
  for (const script of [
    "contract:validate",
    "contract:generate",
    "contract:typecheck",
    "contract:check",
    "bridge:validate",
    "bridge:generate",
    "bridge:typecheck",
    "bridge:check",
    "tokens:validate",
    "tokens:generate",
    "tokens:typecheck",
    "tokens:check",
  ]) {
    assert.equal(typeof root.scripts[script], "string");
  }

  const [commerceApp, smartMall] = await Promise.all([
    readJson("apps/commerce-app/package.json"),
    readJson("apps/smart-mall-h5/package.json"),
  ]);

  for (const manifest of [commerceApp, smartMall]) {
    assert.equal(typeof manifest.scripts.start, "string");
    assert.equal(typeof manifest.scripts.test, "string");
    assert.equal(typeof manifest.scripts["format:check"], "string");
  }
});

test("each service has a minimal health implementation and test", async () => {
  const requiredSnippets = new Map([
    [
      "services/commerce-api/pom.xml",
      ["spring-boot-starter-actuator", "spring-boot-starter-test"],
    ],
    [
      "services/commerce-api/src/test/java/com/hengpick/mall/CommerceApiApplicationTest.java",
      ["/actuator/health", 'jsonPath("$.status").value("UP")'],
    ],
    ["services/agent-service/pyproject.toml", ["fastapi", "pytest"]],
    [
      "services/agent-service/app/main.py",
      ['@application.get("/health/live")', '"status": "UP"'],
    ],
    [
      "services/agent-service/tests/test_health.py",
      ['client.get("/health/live")', '{"status": "UP"}'],
    ],
  ]);

  for (const [path, snippets] of requiredSnippets) {
    const content = await readFile(path, "utf8");
    for (const snippet of snippets) {
      assert.ok(content.includes(snippet), `${path} should contain ${snippet}`);
    }
  }
});

test("README documents independent start commands for all four applications", async () => {
  const readme = await readFile("README.md", "utf8");

  for (const command of [
    "npm run start:commerce-app",
    "npm run start:smart-mall-h5",
    "npm run start:commerce-api",
    "npm run start:agent-service",
  ]) {
    assert.ok(readme.includes(command), `README should include ${command}`);
  }
});
