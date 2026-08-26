import assert from "node:assert/strict";
import { access, constants, readFile } from "node:fs/promises";
import test from "node:test";

import { parse } from "yaml";

const composePath = "deploy/docker-compose.yml";

test("P02-S01 Compose defines only persistent MySQL, Redis, and Qdrant infrastructure", async () => {
  const compose = parse(await readFile(composePath, "utf8"));

  assert.deepEqual(Object.keys(compose.services).sort(), [
    "mysql",
    "qdrant",
    "redis",
  ]);
  assert.deepEqual(Object.keys(compose.volumes).sort(), [
    "mysql-data",
    "qdrant-storage",
    "redis-data",
  ]);
});

test("P02-S01 service ports are configurable and each service has a healthcheck", async () => {
  const compose = parse(await readFile(composePath, "utf8"));

  const requirements = {
    mysql: { port: "MYSQL_PORT", volume: "mysql-data" },
    redis: { port: "REDIS_PORT", volume: "redis-data" },
    qdrant: { port: "QDRANT_HTTP_PORT", volume: "qdrant-storage" },
  };

  for (const [name, requirement] of Object.entries(requirements)) {
    const service = compose.services[name];
    assert.ok(service.ports.join("\n").includes(requirement.port));
    assert.ok(service.volumes.join("\n").includes(requirement.volume));
    assert.ok(service.healthcheck, `${name} requires a container healthcheck`);
    assert.equal(service.healthcheck.interval, "5s");
    assert.equal(service.healthcheck.timeout, "3s");
    assert.equal(service.healthcheck.retries, 20);
  }

  assert.match(
    compose.services.redis.healthcheck.test.join(" "),
    /PONG\|NOAUTH/,
  );
  assert.match(compose.services.qdrant.healthcheck.test.join(" "), /readyz/);
  assert.equal(
    compose.services.qdrant.environment.QDRANT__SERVICE__API_KEY,
    "${QDRANT_API_KEY:?set QDRANT_API_KEY in deploy/.env}",
  );
});

test("P02-S01 configuration documents placeholders instead of real secrets", async () => {
  const environment = await readFile("deploy/.env.example", "utf8");

  for (const variable of [
    "MYSQL_ROOT_PASSWORD",
    "REDIS_PASSWORD",
    "QDRANT_HTTP_PORT",
    "QDRANT_API_KEY",
  ]) {
    assert.match(environment, new RegExp(`^${variable}=`, "m"));
  }
  assert.doesNotMatch(environment, /123456|yang1998/);
});

test("VM entrypoints manage the project Compose without requiring Make", async () => {
  for (const path of [
    "deploy/infra-up",
    "deploy/infra-down",
    "deploy/infra-status",
  ]) {
    await access(path, constants.X_OK);
    const source = await readFile(path, "utf8");
    assert.match(source, /^#!\/usr\/bin\/env sh/m);
    assert.doesNotMatch(source, /docker-compose\.hengpick\.yml/);
    assert.match(source, /docker compose/);
  }
});
