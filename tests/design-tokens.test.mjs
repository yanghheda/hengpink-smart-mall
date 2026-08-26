import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";

import {
  generateArtifacts,
  validateTokens,
} from "../packages/design-tokens/scripts/token-tool.mjs";

const tokenPath = "packages/design-tokens/tokens.json";

test("Design Token source contains the frozen P01-S03 primitives", async () => {
  const tokens = JSON.parse(await readFile(tokenPath, "utf8"));

  assert.doesNotThrow(() => validateTokens(tokens));
  assert.deepEqual(tokens, {
    "color.primary": "#2563EB",
    "color.success": "#16A34A",
    "color.warning": "#D97706",
    "radius.card": 16,
    "space.page": 16,
    "font.body": 14,
    "font.title": 20,
  });
});

test("RN constants and H5 CSS variables are current views of one token source", async () => {
  const tokens = JSON.parse(await readFile(tokenPath, "utf8"));
  const artifacts = generateArtifacts(tokens);

  for (const artifact of artifacts) {
    assert.equal(
      await readFile(artifact.path, "utf8"),
      artifact.content,
      `${artifact.path} is stale`,
    );
  }

  const rn = await readFile(
    "packages/design-tokens/generated/react-native/tokens.ts",
    "utf8",
  );
  const css = await readFile(
    "packages/design-tokens/generated/web/tokens.css",
    "utf8",
  );
  assert.match(rn, /primary: "#2563EB"/);
  assert.match(css, /--color-primary: #2563EB;/);
});

test("Token check rejects drift and invalid token values", async () => {
  const tokens = JSON.parse(await readFile(tokenPath, "utf8"));
  tokens["space.page"] = -1;

  const directory = await mkdtemp(join(tmpdir(), "hengpick-tokens-"));
  const changedTokens = join(directory, "tokens.json");
  await writeFile(
    changedTokens,
    `${JSON.stringify(tokens, null, 2)}\n`,
    "utf8",
  );

  const result = spawnSync(
    process.execPath,
    [
      "packages/design-tokens/scripts/token-tool.mjs",
      "check",
      "--spec",
      changedTokens,
    ],
    { encoding: "utf8" },
  );

  assert.notEqual(result.status, 0);
  assert.match(
    `${result.stdout}${result.stderr}`,
    /space\.page.*non-negative/i,
  );
});
