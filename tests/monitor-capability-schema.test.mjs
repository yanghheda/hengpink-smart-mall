import assert from "node:assert/strict";
import test from "node:test";

import {
  loadCategorySchema,
  mapCapabilityFacts,
  validateCategoryAttributes,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

test("monitor schema defines its own filters, comparisons, and capabilities", async () => {
  const schema = await loadCategorySchema("MONITOR");
  assert.equal(schema.schemaVersion, "monitor-1.0");
  assert.deepEqual(schema.hardConstraintOperators.resolution, ["="]);
  assert.deepEqual(schema.hardConstraintOperators.refreshHz, [">=", "<="]);
  assert.ok(schema.comparisonGroups.some((group) => group.key === "display"));
  assert.deepEqual(
    schema.capabilities.map((item) => item.key),
    ["coding", "image_editing", "eye_comfort"],
  );
});

test("monitor validation rejects phone-only attributes with a precise path", async () => {
  const schema = await loadCategorySchema("MONITOR");
  assert.throws(
    () => validateCategoryAttributes({ batteryMah: 5000 }, schema),
    (error) =>
      /attributes\.batteryMah/.test(error.message) &&
      /not defined by MONITOR schema/.test(error.message),
  );
});

test("monitor capability facts preserve missing values", async () => {
  const schema = await loadCategorySchema("MONITOR");
  const capabilities = mapCapabilityFacts(
    { sizeInch: 27, resolution: "4K", refreshHz: 60 },
    schema,
    "S-MONITOR-1",
  );
  const coding = capabilities.find((item) => item.capability === "coding");
  assert.equal(
    coding.facts.find((fact) => fact.attribute === "pixelDensityPpi").status,
    "MISSING",
  );
  assert.equal(
    coding.facts.find((fact) => fact.attribute === "resolution").fact_id,
    "S-MONITOR-1:resolution",
  );
});
