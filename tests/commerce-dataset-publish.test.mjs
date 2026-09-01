import assert from "node:assert/strict";
import test from "node:test";

import {
  createDatasetReport,
  generateDataset,
  publishDataset,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

test("same seed and version produce the same generated dataset hash", async () => {
  const first = await generateDataset({
    seed: 20260826,
    version: "commerce-demo-2026.08.2",
    generatedProducts: 3,
  });
  const second = await generateDataset({
    seed: 20260826,
    version: "commerce-demo-2026.08.2",
    generatedProducts: 3,
  });
  assert.equal(first.report.content_hash, second.report.content_hash);
  assert.equal(first.dataset.products.length, 23);
  assert.equal(first.dataset.skus.length, 46);
});

test("report contains version, counts, hash, and explicit anomalies", async () => {
  const result = await generateDataset({
    seed: 7,
    version: "commerce-demo-2026.08.3",
    generatedProducts: 1,
  });
  const report = createDatasetReport(result.dataset, {
    seed: 7,
    anomalies: ["missing_attribute", "out_of_stock"],
  });
  assert.equal(report.dataset_version, "commerce-demo-2026.08.3");
  assert.deepEqual(report.counts, {
    categories: 5,
    products: 21,
    skus: 42,
    shops: 2,
    offers: 42,
    reviews: 15,
    knowledge_documents: 19,
  });
  assert.match(report.content_hash, /^[a-f0-9]{64}$/);
  assert.deepEqual(report.anomalies, ["missing_attribute", "out_of_stock"]);
});

test("invalid dataset version cannot become active", async () => {
  const result = await generateDataset({
    seed: 1,
    version: "commerce-demo-2026.08.4",
    generatedProducts: 0,
  });
  result.dataset.dataset_version = "wrong-version";
  assert.throws(
    () => publishDataset(result.dataset),
    /cannot activate.*version/i,
  );
});

test("publish returns an explicit active version only after validation", async () => {
  const result = await generateDataset({
    seed: 1,
    version: "commerce-demo-2026.08.4",
    generatedProducts: 0,
  });
  assert.deepEqual(publishDataset(result.dataset), {
    status: "ACTIVE",
    dataset_version: "commerce-demo-2026.08.4",
    content_hash: result.report.content_hash,
  });
});
