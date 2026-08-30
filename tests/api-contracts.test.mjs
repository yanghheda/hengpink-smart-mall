import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import test from "node:test";

import { parse } from "yaml";

import {
  generateArtifacts,
  validateContract,
} from "../packages/api-contracts/scripts/contract-tool.mjs";

const contractPath = "packages/api-contracts/openapi.yaml";

test("OpenAPI 3.1 defines auth, decision, memory, catalog, and offer slices", async () => {
  const source = await readFile(contractPath, "utf8");
  const contract = parse(source);

  assert.doesNotThrow(() => validateContract(contract));
  assert.match(contract.openapi, /^3\.1\./);
  assert.deepEqual(Object.keys(contract.paths), [
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/smart-mall/tickets",
    "/api/v1/smart-mall/sessions/exchange",
    "/api/v1/decision-sessions/{sessionId}/stream",
    "/api/v1/decision-sessions/{sessionId}",
    "/api/v1/decision-sessions/{sessionId}/memory-proposals",
    "/api/v1/me/memory-proposals/{proposalId}/decision",
    "/api/v1/health",
    "/api/v1/products",
    "/api/v1/products/search",
    "/api/v1/products/compare",
    "/api/v1/products/{productId}/facts",
    "/api/v1/products/{productId}",
    "/api/v1/skus/{skuId}/offers",
  ]);

  const schemas = contract.components.schemas;
  for (const name of [
    "LoginRequest",
    "RefreshRequest",
    "AuthTokens",
    "AuthTokenResponse",
    "CreateSmartMallTicketRequest",
    "SmartMallTicket",
    "SmartMallTicketResponse",
    "ExchangeSmartMallTicketRequest",
    "H5Session",
    "H5SessionResponse",
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
    "ProductComparisonRequest",
    "ProductComparisonResponse",
    "MemoryProposalRequest",
    "MemoryProposalResponse",
    "MemoryDecisionRequest",
    "MemoryDecisionResponse",
    "CatalogFactListResponse",
    "OfferListResponse",
  ]) {
    assert.ok(schemas[name], `missing components.schemas.${name}`);
  }

  assert.deepEqual(schemas.SuccessEnvelope.required, [
    "requestId",
    "data",
    "meta",
  ]);
  assert.deepEqual(schemas.ErrorEnvelope.required, ["requestId", "error"]);
  assert.equal(
    contract.paths["/api/v1/auth/refresh"].post.responses["200"].content[
      "application/json"
    ].schema.$ref,
    "#/components/schemas/AuthTokenResponse",
  );
  assert.equal(
    contract.paths["/api/v1/health"].get.responses["200"].content[
      "application/json"
    ].schema.$ref,
    "#/components/schemas/HealthResponse",
  );

  assert.equal(
    contract.paths["/api/v1/products"].get.responses["200"].content[
      "application/json"
    ].schema.$ref,
    "#/components/schemas/ProductPageResponse",
  );
  assert.equal(
    contract.paths["/api/v1/products/{productId}"].get.responses["404"].content[
      "application/json"
    ].schema.$ref,
    "#/components/schemas/ErrorEnvelope",
  );
  assert.equal(
    contract.paths["/api/v1/skus/{skuId}/offers"].get.responses["200"].content[
      "application/json"
    ].schema.$ref,
    "#/components/schemas/OfferListResponse",
  );
  assert.equal(schemas.OfferView.properties.salePrice.type, "string");
});

test("generated TypeScript and Python types are byte-for-byte current", async () => {
  const source = await readFile(contractPath, "utf8");
  const artifacts = generateArtifacts(parse(source));

  for (const artifact of artifacts) {
    const actual = await readFile(artifact.path, "utf8");
    assert.equal(actual, artifact.content, `${artifact.path} is stale`);
  }
});

test("contract check catches a compatible-looking source edit before generated code drifts", async () => {
  const source = await readFile(contractPath, "utf8");
  const changed = source.replace(
    /requestId:\n(\s+)type: string/,
    "requestId:\n$1type: integer",
  );
  assert.notEqual(
    changed,
    source,
    "fault injection must change requestId type",
  );

  const directory = await mkdtemp(join(tmpdir(), "hengpick-contract-"));
  const changedContract = join(directory, "openapi.yaml");
  await writeFile(changedContract, changed, "utf8");

  const result = spawnSync(
    process.execPath,
    [
      "packages/api-contracts/scripts/contract-tool.mjs",
      "check",
      "--spec",
      changedContract,
    ],
    { encoding: "utf8" },
  );

  assert.notEqual(result.status, 0);
  assert.match(`${result.stdout}${result.stderr}`, /generated contract drift/i);
});
