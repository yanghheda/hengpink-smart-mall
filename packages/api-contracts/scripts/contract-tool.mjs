#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parse } from "yaml";

const repositoryRoot = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const defaultSpecPath = resolve(
  repositoryRoot,
  "packages/api-contracts/openapi.yaml",
);

const generatedPaths = {
  python: "packages/api-contracts/generated/python/api_contracts.py",
  typescript: "packages/api-contracts/generated/typescript/api-contracts.ts",
};

const requiredSchemas = [
  "SuccessEnvelope",
  "ErrorEnvelope",
  "ApiError",
  "ErrorDetail",
  "ResponseMeta",
  "HealthData",
  "HealthResponse",
  "ProductSummary",
  "ProductPageResponse",
  "ProductDetailResponse",
  "CatalogSearchRequest",
  "CatalogSearchResponse",
  "CatalogFactListResponse",
  "OfferListResponse",
];

function referenceName(reference) {
  const prefix = "#/components/schemas/";
  if (typeof reference !== "string" || !reference.startsWith(prefix)) {
    throw new Error(
      `Only local component schema references are supported: ${reference}`,
    );
  }
  return reference.slice(prefix.length);
}

function referencedSchema(document, reference) {
  const name = referenceName(reference);
  const schema = document.components?.schemas?.[name];
  if (!schema) {
    throw new Error(`Unresolved schema reference: ${reference}`);
  }
  return schema;
}

function objectShape(schema, document, seen = new Set()) {
  if (schema.$ref) {
    const name = referenceName(schema.$ref);
    if (seen.has(name)) {
      throw new Error(
        `Recursive object schema is outside P01-S02 scope: ${name}`,
      );
    }
    return objectShape(
      referencedSchema(document, schema.$ref),
      document,
      new Set([...seen, name]),
    );
  }

  const properties = {};
  const required = new Set();
  for (const item of schema.allOf ?? []) {
    const inherited = objectShape(item, document, seen);
    Object.assign(properties, inherited.properties);
    for (const name of inherited.required) required.add(name);
  }
  Object.assign(properties, schema.properties ?? {});
  for (const name of schema.required ?? []) required.add(name);
  return { properties, required };
}

function typescriptType(schema) {
  if (Object.keys(schema).length === 0) return "unknown";
  if (schema.$ref) return referenceName(schema.$ref);
  if (Array.isArray(schema.enum)) {
    return schema.enum.map((value) => JSON.stringify(value)).join(" | ");
  }
  if (schema.type === "array") return `Array<${typescriptType(schema.items)}>`;
  if (schema.type === "boolean") return "boolean";
  if (schema.type === "integer" || schema.type === "number") return "number";
  if (schema.type === "string") return "string";
  if (schema.type === "object") return "Record<string, unknown>";
  throw new Error(`Unsupported TypeScript schema: ${JSON.stringify(schema)}`);
}

function pythonType(schema) {
  if (Object.keys(schema).length === 0) return "object";
  if (schema.$ref) return referenceName(schema.$ref);
  if (Array.isArray(schema.enum)) {
    return `Literal[${schema.enum.map((value) => JSON.stringify(value)).join(", ")}]`;
  }
  if (schema.type === "array") return `list[${pythonType(schema.items)}]`;
  if (schema.type === "boolean") return "bool";
  if (schema.type === "integer") return "int";
  if (schema.type === "number") return "float";
  if (schema.type === "string") return "str";
  if (schema.type === "object") return "dict[str, object]";
  throw new Error(`Unsupported Python schema: ${JSON.stringify(schema)}`);
}

function sourceDigest(document) {
  return createHash("sha256").update(JSON.stringify(document)).digest("hex");
}

function generateTypescript(document) {
  const lines = [
    "// 由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。",
    `// 契约源摘要：${sourceDigest(document)}`,
    "",
  ];
  for (const [name, schema] of Object.entries(document.components.schemas)) {
    const { properties, required } = objectShape(schema, document);
    lines.push(`export interface ${name} {`);
    for (const [propertyName, propertySchema] of Object.entries(properties)) {
      const optional = required.has(propertyName) ? "" : "?";
      lines.push(
        `  ${propertyName}${optional}: ${typescriptType(propertySchema)};`,
      );
    }
    lines.push("}", "");
  }
  return `${lines.join("\n").trimEnd()}\n`;
}

function generatePython(document) {
  const lines = [
    '"""由 packages/api-contracts/openapi.yaml 生成，请勿手工修改。"""',
    "",
    `# 契约源摘要：${sourceDigest(document)}`,
    "from typing import Literal, NotRequired, TypedDict",
    "",
    "",
  ];
  for (const [name, schema] of Object.entries(document.components.schemas)) {
    const { properties, required } = objectShape(schema, document);
    lines.push(`class ${name}(TypedDict):`);
    if (Object.keys(properties).length === 0) lines.push("    pass");
    for (const [propertyName, propertySchema] of Object.entries(properties)) {
      const type = pythonType(propertySchema);
      lines.push(
        required.has(propertyName)
          ? `    ${propertyName}: ${type}`
          : `    ${propertyName}: NotRequired[${type}]`,
      );
    }
    lines.push("", "");
  }
  return `${lines.join("\n").trimEnd()}\n`;
}

export function validateContract(document) {
  if (!document || typeof document !== "object") {
    throw new Error("OpenAPI document must be an object");
  }
  if (!/^3\.1\./.test(document.openapi ?? "")) {
    throw new Error("P01-S02 requires OpenAPI 3.1.x");
  }
  if (!document.info?.title || !document.info?.version) {
    throw new Error("OpenAPI info.title and info.version are required");
  }
  const paths = Object.keys(document.paths ?? {});
  const expectedPaths = [
    "/api/v1/health",
    "/api/v1/products",
    "/api/v1/products/search",
    "/api/v1/products/{productId}/facts",
    "/api/v1/products/{productId}",
    "/api/v1/skus/{skuId}/offers",
  ];
  if (JSON.stringify(paths) !== JSON.stringify(expectedPaths)) {
    throw new Error(`Public paths must be: ${expectedPaths.join(", ")}`);
  }
  const operation = document.paths["/api/v1/health"]?.get;
  if (!operation?.operationId) {
    throw new Error("GET /api/v1/health requires operationId");
  }
  const successReference =
    operation.responses?.["200"]?.content?.["application/json"]?.schema?.$ref;
  if (successReference !== "#/components/schemas/HealthResponse") {
    throw new Error("GET /api/v1/health 200 must use HealthResponse");
  }
  const schemas = document.components?.schemas ?? {};
  for (const name of requiredSchemas) {
    if (!schemas[name]) throw new Error(`Missing required schema: ${name}`);
  }
  for (const [name, required] of [
    ["SuccessEnvelope", ["requestId", "data", "meta"]],
    ["ErrorEnvelope", ["requestId", "error"]],
  ]) {
    const actual = schemas[name].required ?? [];
    if (required.some((property) => !actual.includes(property))) {
      throw new Error(`${name} is missing required envelope fields`);
    }
  }
  for (const schema of Object.values(schemas)) objectShape(schema, document);
}

export function generateArtifacts(document) {
  validateContract(document);
  return [
    {
      path: generatedPaths.typescript,
      content: generateTypescript(document),
    },
    { path: generatedPaths.python, content: generatePython(document) },
  ];
}

async function loadDocument(specPath) {
  const source = await readFile(specPath, "utf8");
  const document = parse(source);
  validateContract(document);
  return document;
}

async function generate(specPath) {
  const document = await loadDocument(specPath);
  for (const artifact of generateArtifacts(document)) {
    const target = resolve(repositoryRoot, artifact.path);
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, artifact.content, "utf8");
    console.log(`generated ${artifact.path}`);
  }
}

async function check(specPath) {
  const document = await loadDocument(specPath);
  const drift = [];
  for (const artifact of generateArtifacts(document)) {
    let actual;
    try {
      actual = await readFile(resolve(repositoryRoot, artifact.path), "utf8");
    } catch {
      actual = undefined;
    }
    if (actual !== artifact.content) drift.push(artifact.path);
  }
  if (drift.length > 0) {
    throw new Error(`Generated contract drift: ${drift.join(", ")}`);
  }
  console.log("OpenAPI is valid and generated contract types are current.");
}

function argumentValue(name) {
  const index = process.argv.indexOf(name);
  return index === -1 ? undefined : process.argv[index + 1];
}

async function main() {
  const command = process.argv[2];
  const specPath = resolve(argumentValue("--spec") ?? defaultSpecPath);
  if (command === "generate") return generate(specPath);
  if (command === "check") return check(specPath);
  if (command === "validate") {
    await loadDocument(specPath);
    console.log("OpenAPI 3.1 contract is valid.");
    return;
  }
  throw new Error(
    "Usage: contract-tool.mjs <generate|check|validate> [--spec path]",
  );
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : undefined;
if (invokedPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
