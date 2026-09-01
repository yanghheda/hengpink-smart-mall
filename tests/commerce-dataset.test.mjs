import assert from "node:assert/strict";
import test from "node:test";

import {
  buildKnowledgeChunks,
  loadCuratedDataset,
  validateDataset,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

test("curated dataset has the minimum P03 phone slice", async () => {
  const dataset = await loadCuratedDataset();
  assert.equal(dataset.dataset_version, "commerce-demo-2026.09.2");
  assert.ok(dataset.categories.some((item) => item.category_id === "PHONE"));
  const phoneProductIds = new Set(
    dataset.products
      .filter((item) => item.category_id === "PHONE")
      .map((item) => item.product_id),
  );
  assert.equal(phoneProductIds.size, 6);
  assert.equal(
    dataset.skus.filter((item) => phoneProductIds.has(item.product_id)).length,
    12,
  );
  assert.ok(dataset.shops.length >= 2);
  assert.ok(dataset.offers.length >= 12);
  assert.doesNotThrow(() => validateDataset(dataset));
});

test("curated dataset adds a monitor slice without changing the phone slice", async () => {
  const dataset = await loadCuratedDataset();
  const categoryIds = dataset.categories.map((item) => item.category_id);
  const monitorProducts = dataset.products.filter(
    (item) => item.category_id === "MONITOR",
  );
  const monitorProductIds = new Set(
    monitorProducts.map((item) => item.product_id),
  );
  const monitorSkus = dataset.skus.filter((item) =>
    monitorProductIds.has(item.product_id),
  );

  assert.deepEqual(categoryIds.slice(0, 2), ["PHONE", "MONITOR"]);
  assert.equal(
    dataset.products.filter((item) => item.category_id === "PHONE").length,
    6,
  );
  assert.equal(monitorProducts.length, 2);
  assert.equal(monitorSkus.length, 4);
  assert.doesNotThrow(() => validateDataset(dataset));
});

test("knowledge document cannot bind a SKU from another product", async () => {
  const dataset = await loadCuratedDataset();
  dataset.knowledge_documents[0].sku_id = dataset.skus[2].sku_id;

  assert.throws(
    () => validateDataset(dataset),
    (error) =>
      /knowledge_documents\[0\]\.sku_id/.test(error.message) &&
      /does not belong to product/i.test(error.message),
  );
});

test("typed chunks keep one evidence boundary and stable content hashes", async () => {
  const dataset = await loadCuratedDataset();
  const first = buildKnowledgeChunks(dataset);
  const second = buildKnowledgeChunks(dataset);

  assert.deepEqual(first, second);
  assert.ok(first.length >= dataset.knowledge_documents.length);
  assert.ok(first.every((chunk) => /^[a-f0-9]{64}$/.test(chunk.content_hash)));
  assert.ok(first.every((chunk) => chunk.is_simulated === true));
  assert.equal(
    new Set(first.map((chunk) => chunk.chunk_id)).size,
    first.length,
  );
});

test("unknown SKU attribute reports its JSON path and reason", async () => {
  const dataset = await loadCuratedDataset();
  dataset.skus[0].attributes.notDefined = true;

  assert.throws(
    () => validateDataset(dataset),
    (error) =>
      /skus\[0\]\.attributes\.notDefined/.test(error.message) &&
      /not defined by category schema/i.test(error.message),
  );
});

test("cross-product review reference is rejected with a precise path", async () => {
  const dataset = await loadCuratedDataset();
  dataset.reviews[0].sku_id = dataset.skus[2].sku_id;

  assert.throws(
    () => validateDataset(dataset),
    (error) =>
      /reviews\[0\]\.sku_id/.test(error.message) &&
      /does not belong to product/i.test(error.message),
  );
});

test("every entity carries dataset version and updated timestamp", async () => {
  const dataset = await loadCuratedDataset();
  delete dataset.shops[0].updated_at;

  assert.throws(
    () => validateDataset(dataset),
    (error) => /shops\[0\]\.updated_at/.test(error.message),
  );
});

test("offer validity uses an ordered half-open time window", async () => {
  const dataset = await loadCuratedDataset();
  dataset.offers[0].valid_to = dataset.offers[0].valid_from;

  assert.throws(
    () => validateDataset(dataset),
    (error) =>
      /offers\[0\]\.valid_to/.test(error.message) &&
      /later than valid_from/i.test(error.message),
  );
});
