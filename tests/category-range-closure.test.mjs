import assert from "node:assert/strict";
import test from "node:test";

import {
  loadCategorySchema,
  loadCuratedDataset,
  mapCapabilityFacts,
  validateCategoryAttributes,
  validateDataset,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

const CATEGORY_IDS = [
  "PHONE",
  "MONITOR",
  "HEADPHONE",
  "AIR_PURIFIER",
  "OFFICE_CHAIR",
];

test("P15-S02 closes the range with five explicitly deep categories", async () => {
  const dataset = await loadCuratedDataset();
  assert.deepEqual(
    dataset.categories.map((item) => item.category_id),
    CATEGORY_IDS,
  );
  assert.ok(
    dataset.categories.every((item) => item.schema_coverage === "DEEP"),
  );
  assert.ok(
    dataset.categories.every(
      (item) =>
        item.confidence_policy.strategy === "SCHEMA_COVERAGE" &&
        item.confidence_policy.deep_threshold === 0.6 &&
        item.confidence_policy.deep_max_level === "HIGH" &&
        item.confidence_policy.fallback_max_level === "MEDIUM",
    ),
  );
  assert.ok(dataset.products.length >= 20);
  assert.ok(dataset.skus.length >= 40);
  assert.ok(dataset.offers.length >= 40);
  assert.doesNotThrow(() => validateDataset(dataset));
});

test("every category declaration resolves to the same versioned schema", async () => {
  const dataset = await loadCuratedDataset();
  for (const category of dataset.categories) {
    const schema = await loadCategorySchema(category.category_id);
    assert.equal(schema.categoryId, category.category_id);
    assert.equal(schema.schemaVersion, category.schema_version);
    assert.ok(schema.attributes.length >= 8);
    assert.ok(schema.capabilities.length >= 3);
  }
});

test("new categories expose distinct capability facts without cross-category leakage", async () => {
  const cases = [
    ["HEADPHONE", { noiseCancellationDb: 42 }, "noise_cancellation"],
    ["AIR_PURIFIER", { cadrM3h: 500 }, "purification"],
    ["OFFICE_CHAIR", { lumbarSupportAdjustable: true }, "ergonomics"],
  ];
  for (const [categoryId, attributes, capabilityKey] of cases) {
    const schema = await loadCategorySchema(categoryId);
    assert.doesNotThrow(() => validateCategoryAttributes(attributes, schema));
    assert.ok(
      mapCapabilityFacts(attributes, schema, `S-${categoryId}`).some(
        (item) => item.capability === capabilityKey,
      ),
    );
    assert.throws(
      () => validateCategoryAttributes({ batteryMah: 5000 }, schema),
      /attributes\.batteryMah/,
    );
  }
});

test("every added product has two SKUs, offers, review, evidence, and simulated declaration", async () => {
  const dataset = await loadCuratedDataset();
  for (const categoryId of CATEGORY_IDS.slice(2)) {
    const products = dataset.products.filter(
      (item) => item.category_id === categoryId,
    );
    assert.ok(products.length >= 4);
    for (const product of products) {
      const skus = dataset.skus.filter(
        (item) => item.product_id === product.product_id,
      );
      assert.equal(skus.length, 2);
      assert.ok(
        skus.every((sku) =>
          dataset.offers.some((offer) => offer.sku_id === sku.sku_id),
        ),
      );
      assert.ok(
        dataset.reviews.some(
          (review) => review.product_id === product.product_id,
        ),
      );
      assert.ok(
        dataset.knowledge_documents.some(
          (item) => item.product_id === product.product_id,
        ),
      );
    }
  }
  assert.ok(
    dataset.knowledge_documents.every((item) => item.is_simulated === true),
  );
});

test("every SKU has an active offer throughout the P15 demo window", async () => {
  const dataset = await loadCuratedDataset();
  const demoAt = Date.parse("2026-09-01T14:00:00Z");
  for (const sku of dataset.skus) {
    const validOffers = dataset.offers.filter(
      (offer) =>
        offer.sku_id === sku.sku_id &&
        Date.parse(offer.valid_from) <= demoAt &&
        demoAt < Date.parse(offer.valid_to),
    );
    assert.ok(validOffers.length > 0, `${sku.sku_id} has no active offer`);
  }
});
