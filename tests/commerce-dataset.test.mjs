import assert from "node:assert/strict";
import test from "node:test";

import {
  loadCuratedDataset,
  validateDataset,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

test("curated dataset has the minimum P03 phone slice", async () => {
  const dataset = await loadCuratedDataset();
  assert.equal(dataset.dataset_version, "commerce-demo-2026.08.1");
  assert.equal(dataset.categories.length, 1);
  assert.equal(dataset.products.length, 6);
  assert.equal(dataset.skus.length, 12);
  assert.ok(dataset.shops.length >= 2);
  assert.ok(dataset.offers.length >= 12);
  assert.doesNotThrow(() => validateDataset(dataset));
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
