import assert from "node:assert/strict";
import test from "node:test";

import {
  loadPhoneSchema,
  mapCapabilityFacts,
  validatePhoneAttributes,
} from "../packages/commerce-dataset/scripts/dataset-tool.mjs";

test("phone schema exposes typed attributes, operators, groups, and four capabilities", async () => {
  const schema = await loadPhoneSchema();
  assert.equal(schema.categoryId, "PHONE");
  assert.equal(schema.schemaVersion, "phone-1.0");
  assert.ok(schema.attributes.some((item) => item.key === "batteryMah"));
  assert.deepEqual(schema.hardConstraintOperators.batteryMah, [">=", "<="]);
  assert.ok(schema.comparisonGroups.some((group) => group.key === "battery"));
  assert.deepEqual(
    schema.capabilities.map((capability) => capability.key),
    ["battery", "easy_use", "camera", "service"],
  );
});

test("undefined phone attribute is rejected with its JSON path", async () => {
  const schema = await loadPhoneSchema();
  assert.throws(
    () =>
      validatePhoneAttributes({ batteryMah: 5000, inventedMetric: 1 }, schema),
    (error) => /attributes\.inventedMetric/.test(error.message),
  );
});

test("capability facts preserve missing state instead of converting it to zero", async () => {
  const schema = await loadPhoneSchema();
  const facts = mapCapabilityFacts(
    { batteryMah: 5000, wirelessCharging: true, ois: false },
    schema,
    "S-DEMO-1",
  );
  const battery = facts.find((fact) => fact.capability === "battery");
  assert.equal(
    battery.facts.find((fact) => fact.attribute === "chargingW").value,
    null,
  );
  assert.equal(
    battery.facts.find((fact) => fact.attribute === "chargingW").status,
    "MISSING",
  );
  assert.equal(
    battery.facts.find((fact) => fact.attribute === "batteryMah").fact_id,
    "S-DEMO-1:batteryMah",
  );
});

test("boolean source facts retain boolean value and explicit numeric mapping", async () => {
  const schema = await loadPhoneSchema();
  const facts = mapCapabilityFacts(
    { ois: true, telephoto: false },
    schema,
    "S-DEMO-2",
  );
  const cameraFacts = facts.find((fact) => fact.capability === "camera").facts;
  assert.deepEqual(
    cameraFacts.find((fact) => fact.attribute === "ois"),
    {
      fact_id: "S-DEMO-2:ois",
      attribute: "ois",
      value: true,
      numeric_value: 100,
      status: "KNOWN",
    },
  );
  assert.equal(
    cameraFacts.find((fact) => fact.attribute === "telephoto").numeric_value,
    0,
  );
});
