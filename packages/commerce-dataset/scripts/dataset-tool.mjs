import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";

const fixturePath = fileURLToPath(
  new URL("../fixtures/curated/commerce-demo-2026.09.1.json", import.meta.url),
);
const schemaDirectory = new URL("../schemas/", import.meta.url);

export async function loadCuratedDataset() {
  return JSON.parse(await readFile(fixturePath, "utf8"));
}

export async function loadPhoneSchema() {
  return loadCategorySchema("PHONE");
}

export async function loadCategorySchema(categoryId) {
  const fileName = `${categoryId.toLowerCase()}.schema.json`;
  const schema = JSON.parse(
    await readFile(new URL(fileName, schemaDirectory), "utf8"),
  );
  if (schema.categoryId !== categoryId)
    fail("categoryId", `must match requested category ${categoryId}`);
  return schema;
}

function stableHash(value) {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

export function createDatasetReport(dataset, options = {}) {
  return {
    dataset_version: dataset.dataset_version,
    seed: options.seed ?? null,
    counts: Object.fromEntries(
      [
        "categories",
        "products",
        "skus",
        "shops",
        "offers",
        "reviews",
        "knowledge_documents",
      ].map((name) => [name, dataset[name].length]),
    ),
    content_hash: stableHash(dataset),
    anomalies: options.anomalies ?? [],
  };
}

export async function generateDataset({
  seed,
  version,
  generatedProducts = 0,
}) {
  const base = await loadCuratedDataset();
  const dataset = structuredClone(base);
  dataset.dataset_version = version;
  dataset.updated_at = "2026-08-26T00:00:00Z";
  for (const name of [
    "categories",
    "products",
    "skus",
    "shops",
    "offers",
    "reviews",
    "knowledge_documents",
  ]) {
    for (const entity of dataset[name]) {
      entity.dataset_version = version;
      entity.updated_at = dataset.updated_at;
    }
  }
  for (let index = 0; index < generatedProducts; index += 1) {
    const productId = `P-GENERATED-${seed}-${index}`;
    const product = {
      product_id: productId,
      category_id: "PHONE",
      brand: `SeedBrand${seed % 10}`,
      model: `G${index + 1}`,
      dataset_version: version,
      updated_at: dataset.updated_at,
    };
    dataset.products.push(product);
    for (const variant of ["128-B", "256-W"]) {
      const skuId = `S-GENERATED-${seed}-${index}-${variant}`;
      dataset.skus.push({
        sku_id: skuId,
        product_id: productId,
        dataset_version: version,
        updated_at: dataset.updated_at,
        attributes: {
          ramGb: variant.startsWith("128") ? 8 : 12,
          storageGb: Number(variant.slice(0, 3)),
          batteryMah: 4700 + ((seed + index) % 4) * 100,
          color: variant.endsWith("B") ? "black" : "white",
        },
      });
      dataset.offers.push({
        offer_id: `O-GENERATED-${seed}-${index}-${variant}`,
        sku_id: skuId,
        shop_id: dataset.shops[index % dataset.shops.length].shop_id,
        list_price: variant.startsWith("128") ? "2099.00" : "2399.00",
        price: variant.startsWith("128") ? "1899.00" : "2199.00",
        additional_fee: "0.00",
        currency: "CNY",
        stock_status: "IN_STOCK",
        valid_from: "2026-08-01T00:00:00Z",
        valid_to: "2026-09-01T00:00:00Z",
        version: 0,
        dataset_version: version,
        updated_at: dataset.updated_at,
      });
    }
  }
  validateDataset(dataset);
  return { dataset, report: createDatasetReport(dataset, { seed }) };
}

export function publishDataset(dataset) {
  if (!/^commerce-demo-\d{4}\.\d{2}\.\d+$/.test(dataset?.dataset_version ?? ""))
    throw new Error("cannot activate dataset: invalid version");
  validateDataset(dataset);
  return {
    status: "ACTIVE",
    dataset_version: dataset.dataset_version,
    content_hash: stableHash(dataset),
  };
}

export function validatePhoneAttributes(attributes, schema) {
  return validateCategoryAttributes(attributes, schema);
}

export function validateCategoryAttributes(attributes, schema) {
  const definitions = new Map(
    schema.attributes.map((attribute) => [attribute.key, attribute]),
  );
  for (const [key, value] of Object.entries(attributes)) {
    const path = `attributes.${key}`;
    const definition = definitions.get(key);
    if (!definition) fail(path, `not defined by ${schema.categoryId} schema`);
    if (
      definition.type === "number" &&
      (typeof value !== "number" || !Number.isFinite(value))
    )
      fail(path, "must be a finite number");
    if (definition.type === "boolean" && typeof value !== "boolean")
      fail(path, "must be a boolean");
    if (definition.type === "enum" && !definition.enumValues.includes(value))
      fail(path, "must be a supported enum value");
  }
  return attributes;
}

export function mapCapabilityFacts(attributes, schema, skuId) {
  validateCategoryAttributes(attributes, schema);
  return schema.capabilities.map((capability) => ({
    capability: capability.key,
    formula_version: capability.formulaVersion,
    missing_data_policy: capability.missingDataPolicy,
    facts: capability.inputAttributes.map((attribute) => {
      const value = attributes[attribute];
      return {
        fact_id: `${skuId}:${attribute}`,
        attribute,
        value: value ?? null,
        ...(typeof value === "boolean"
          ? { numeric_value: value ? 100 : 0 }
          : {}),
        status: value === undefined ? "MISSING" : "KNOWN",
      };
    }),
  }));
}

function fail(path, reason) {
  throw new Error(`${path}: ${reason}`);
}

function requireField(value, path) {
  if (value === undefined || value === null || value === "")
    fail(path, "is required");
}

export function validateDataset(dataset) {
  requireField(dataset?.dataset_version, "dataset_version");
  requireField(dataset?.updated_at, "updated_at");
  if (!/^commerce-demo-\d{4}\.\d{2}\.\d+$/.test(dataset.dataset_version))
    fail("dataset_version", "has invalid format");

  const collections = [
    "categories",
    "products",
    "skus",
    "shops",
    "offers",
    "reviews",
    "knowledge_documents",
  ];
  for (const name of collections) {
    if (!Array.isArray(dataset[name])) fail(name, "must be an array");
    dataset[name].forEach((entity, index) => {
      const path = `${name}[${index}]`;
      for (const field of ["dataset_version", "updated_at"])
        requireField(entity[field], `${path}.${field}`);
      if (entity.dataset_version !== dataset.dataset_version)
        fail(`${path}.dataset_version`, "must match dataset_version");
    });
  }

  const categories = new Map(dataset.categories.map((c) => [c.category_id, c]));
  const products = new Map(dataset.products.map((p) => [p.product_id, p]));
  const skus = new Map(dataset.skus.map((s) => [s.sku_id, s]));
  const shops = new Set(dataset.shops.map((s) => s.shop_id));
  for (const [i, product] of dataset.products.entries()) {
    requireField(product.category_id, `products[${i}].category_id`);
    if (!categories.has(product.category_id))
      fail(`products[${i}].category_id`, "references unknown category");
  }
  for (const [i, sku] of dataset.skus.entries()) {
    requireField(sku.product_id, `skus[${i}].product_id`);
    const product = products.get(sku.product_id);
    if (!product) fail(`skus[${i}].product_id`, "references unknown product");
    const category = categories.get(product.category_id);
    for (const key of Object.keys(sku.attributes ?? {})) {
      if (!category.attributes.includes(key))
        fail(`skus[${i}].attributes.${key}`, "not defined by category schema");
    }
  }
  for (const [i, offer] of dataset.offers.entries()) {
    if (!skus.has(offer.sku_id))
      fail(`offers[${i}].sku_id`, "references unknown SKU");
    if (!shops.has(offer.shop_id))
      fail(`offers[${i}].shop_id`, "references unknown shop");
    for (const field of ["list_price", "price", "additional_fee"]) {
      if (!/^\d+\.\d{2}$/.test(offer[field]) || Number(offer[field]) < 0)
        fail(
          `offers[${i}].${field}`,
          "must be a non-negative decimal string with two places",
        );
    }
    const validFrom = Date.parse(offer.valid_from);
    const validTo = Date.parse(offer.valid_to);
    if (!Number.isFinite(validFrom) || !Number.isFinite(validTo))
      fail(`offers[${i}]`, "valid_from and valid_to must be date-time values");
    if (validFrom >= validTo)
      fail(`offers[${i}].valid_to`, "must be later than valid_from");
    if (!Number.isInteger(offer.version) || offer.version < 0)
      fail(`offers[${i}].version`, "must be a non-negative integer");
  }
  for (const [i, review] of dataset.reviews.entries()) {
    if (review.product_id && !products.has(review.product_id))
      fail(`reviews[${i}].product_id`, "references unknown product");
    if (review.sku_id) {
      const sku = skus.get(review.sku_id);
      if (!sku) fail(`reviews[${i}].sku_id`, "references unknown SKU");
      if (review.product_id && sku.product_id !== review.product_id)
        fail(`reviews[${i}].sku_id`, "does not belong to product");
    }
  }
  const evidenceIds = new Set();
  const sourceTypes = new Set([
    "SPECIFICATION",
    "SIMULATED_REVIEW",
    "FAQ",
    "AFTER_SALES_POLICY",
    "EXPERT_SUMMARY",
  ]);
  for (const [i, document] of dataset.knowledge_documents.entries()) {
    const path = `knowledge_documents[${i}]`;
    for (const field of [
      "evidence_id",
      "product_id",
      "category_id",
      "source_type",
      "topic",
      "published_at",
      "content",
    ])
      requireField(document[field], `${path}.${field}`);
    if (evidenceIds.has(document.evidence_id))
      fail(`${path}.evidence_id`, "must be globally unique");
    evidenceIds.add(document.evidence_id);
    const product = products.get(document.product_id);
    if (!product) fail(`${path}.product_id`, "references unknown product");
    if (product.category_id !== document.category_id)
      fail(`${path}.category_id`, "does not belong to product");
    if (document.sku_id) {
      const sku = skus.get(document.sku_id);
      if (!sku) fail(`${path}.sku_id`, "references unknown SKU");
      if (sku.product_id !== document.product_id)
        fail(`${path}.sku_id`, "does not belong to product");
    }
    if (!sourceTypes.has(document.source_type))
      fail(`${path}.source_type`, "is not supported in P11-S01");
    if (document.is_simulated !== true)
      fail(`${path}.is_simulated`, "must be true for the demo dataset");
    if (!(document.trust_level >= 0 && document.trust_level <= 1))
      fail(`${path}.trust_level`, "must be between 0 and 1");
  }
  return dataset;
}

function normalizeKnowledgeContent(content) {
  return content.trim().replace(/\s+/g, " ");
}

export function buildKnowledgeChunks(dataset) {
  validateDataset(dataset);
  return dataset.knowledge_documents.flatMap((document) => {
    const content = normalizeKnowledgeContent(document.content);
    const parts = content.split(/\n\s*\n/).filter(Boolean);
    return parts.map((part, index) => ({
      chunk_id: `${document.evidence_id}-C${String(index + 1).padStart(3, "0")}`,
      evidence_id: document.evidence_id,
      product_id: document.product_id,
      sku_id: document.sku_id ?? null,
      category_id: document.category_id,
      source_type: document.source_type,
      topic: document.topic,
      sentiment: document.sentiment ?? null,
      trust_level: document.trust_level,
      published_at: document.published_at,
      dataset_version: document.dataset_version,
      content: part,
      content_hash: createHash("sha256").update(part).digest("hex"),
      is_simulated: true,
    }));
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  validateDataset(JSON.parse(await readFile(fixturePath, "utf8")));
  console.log("curated dataset is valid");
}
