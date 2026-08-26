import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { pathToFileURL } from "node:url";

const defaultTokenPath = "packages/design-tokens/tokens.json";
const expectedKeys = [
  "color.primary",
  "color.success",
  "color.warning",
  "radius.card",
  "space.page",
  "font.body",
  "font.title",
];

export function validateTokens(tokens) {
  if (!tokens || typeof tokens !== "object" || Array.isArray(tokens)) {
    throw new Error("Design Token source must be an object");
  }
  if (JSON.stringify(Object.keys(tokens)) !== JSON.stringify(expectedKeys)) {
    throw new Error(
      `P01-S03 tokens must be exactly: ${expectedKeys.join(", ")}`,
    );
  }

  for (const key of expectedKeys) {
    const value = tokens[key];
    if (key.startsWith("color.")) {
      if (typeof value !== "string" || !/^#[0-9A-F]{6}$/.test(value)) {
        throw new Error(`${key} must be an uppercase six-digit hex color`);
      }
    } else if (!Number.isFinite(value) || value < 0) {
      throw new Error(`${key} must be a finite non-negative number`);
    }
  }
  return tokens;
}

function groupedTokens(tokens) {
  const grouped = {};
  for (const [key, value] of Object.entries(tokens)) {
    const [group, name] = key.split(".");
    grouped[group] ??= {};
    grouped[group][name] = value;
  }
  return grouped;
}

function typescriptArtifact(tokens) {
  const grouped = groupedTokens(tokens);
  const groups = Object.entries(grouped)
    .map(([group, values]) => {
      const properties = Object.entries(values)
        .map(([name, value]) => `    ${name}: ${JSON.stringify(value)},`)
        .join("\n");
      return `  ${group}: {\n${properties}\n  },`;
    })
    .join("\n");
  return `// Generated from tokens.json. Do not edit.\n\nexport const designTokens = {\n${groups}\n} as const;\n\nexport type DesignTokens = typeof designTokens;\n`;
}

function cssArtifact(tokens) {
  const variables = Object.entries(tokens)
    .map(([key, value]) => {
      const name = key.replaceAll(".", "-");
      const rendered = typeof value === "number" ? `${value}px` : value;
      return `  --${name}: ${rendered};`;
    })
    .join("\n");
  return `/* Generated from tokens.json. Do not edit. */\n:root {\n${variables}\n}\n`;
}

export function generateArtifacts(tokens) {
  validateTokens(tokens);
  return [
    {
      path: "packages/design-tokens/generated/react-native/tokens.ts",
      content: typescriptArtifact(tokens),
    },
    {
      path: "packages/design-tokens/generated/web/tokens.css",
      content: cssArtifact(tokens),
    },
  ];
}

async function run(command, tokenPath) {
  const tokens = JSON.parse(await readFile(tokenPath, "utf8"));
  const artifacts = generateArtifacts(tokens);
  if (command === "validate") {
    console.log("Design Token source is valid.");
    return;
  }
  if (command === "generate") {
    await Promise.all(
      artifacts.map(async (artifact) => {
        await mkdir(dirname(artifact.path), { recursive: true });
        await writeFile(artifact.path, artifact.content);
      }),
    );
    console.log("Generated RN constants and H5 CSS variables.");
    return;
  }
  if (command === "check") {
    for (const artifact of artifacts) {
      let current;
      try {
        current = await readFile(artifact.path, "utf8");
      } catch {
        throw new Error(
          `generated Design Token drift: missing ${artifact.path}`,
        );
      }
      if (current !== artifact.content) {
        throw new Error(
          `generated Design Token drift: ${artifact.path} is stale`,
        );
      }
    }
    console.log(
      "Design Token source is valid and generated artifacts are current.",
    );
    return;
  }
  throw new Error(
    "Usage: token-tool.mjs <validate|generate|check> [--spec path]",
  );
}

const isEntrypoint = process.argv[1]
  ? import.meta.url === pathToFileURL(process.argv[1]).href
  : false;
if (isEntrypoint) {
  const command = process.argv[2];
  const specIndex = process.argv.indexOf("--spec");
  const tokenPath =
    specIndex === -1 ? defaultTokenPath : process.argv[specIndex + 1];
  run(command, tokenPath).catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
