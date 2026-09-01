package com.hengpick.mall.catalog.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class CommerceDatasetImporter {
    private static final String DEFAULT_DATASET_RELATIVE_PATH = "packages/commerce-dataset/fixtures/curated/commerce-demo-2026.09.1.json";
    private static final String RANGE_CLOSURE_RELATIVE_PATH =
            "packages/commerce-dataset/fixtures/curated/p15-s02-range-closure.json";
    private static final String CATEGORY_SCHEMA_DIRECTORY = "packages/commerce-dataset/schemas";

    private CommerceDatasetImporter() {}

    public static void main(String[] args) throws Exception {
        var datasetPath = args.length == 0 ? locateDefaultDataset() : Path.of(args[0]);
        var url = requiredEnvironment("MYSQL_URL");
        var username = requiredEnvironment("MYSQL_USERNAME");
        var password = requiredEnvironment("MYSQL_PASSWORD");
        var mapper = new ObjectMapper();
        var dataset = mergeRangeClosure(
                mapper, mapper.readTree(datasetPath.toFile()), mapper.readTree(locateProjectFile(RANGE_CLOSURE_RELATIVE_PATH).toFile()));

        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                importCategories(connection, dataset, mapper);
                importProducts(connection, dataset, mapper);
                importSkus(connection, dataset, mapper);
                importShops(connection, dataset);
                importOffers(connection, dataset);
                importReviews(connection, dataset);
                importKnowledgeDocuments(connection, dataset);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        System.out.printf("已导入 Dataset：%s（%s）%n", dataset.path("dataset_version").asText(), datasetPath);
    }

    /** 将 P15-S02 的类目扩展片段组合成同一个可发布数据版本。 */
    static JsonNode mergeRangeClosure(ObjectMapper mapper, JsonNode base, JsonNode expansion) {
        var dataset = (ObjectNode) base.deepCopy();
        var datasetVersion = expansion.path("dataset_version").asText();
        var updatedAt = expansion.path("updated_at").asText();
        dataset.put("dataset_version", datasetVersion);
        dataset.put("updated_at", updatedAt);
        for (var collection : new String[] {"categories", "products", "skus"}) {
            expansion.withArray(collection).forEach(entity -> dataset.withArray(collection).add(entity.deepCopy()));
        }
        dataset.withArray("categories").forEach(category -> {
            ((ObjectNode) category).put("schema_coverage", "DEEP");
            var policy = ((ObjectNode) category).putObject("confidence_policy");
            policy.put("strategy", "SCHEMA_COVERAGE");
            policy.put("deep_threshold", 0.6);
            policy.put("deep_max_level", "HIGH");
            policy.put("fallback_max_level", "MEDIUM");
        });
        for (var product : expansion.withArray("products")) {
            var productId = product.path("product_id").asText();
            var content = expansion.path("product_content").path(productId);
            var productSkus = new java.util.ArrayList<JsonNode>();
            expansion.withArray("skus").forEach(sku -> {
                if (productId.equals(sku.path("product_id").asText())) productSkus.add(sku);
            });
            for (var index = 0; index < productSkus.size(); index++) {
                var skuId = productSkus.get(index).path("sku_id").asText();
                var offer = mapper.createObjectNode();
                offer.put("offer_id", "O-" + skuId.substring(2));
                offer.put("sku_id", skuId);
                offer.put("shop_id", index == 0 ? "SHOP-DIRECT" : "SHOP-CARE");
                offer.put("list_price", content.path("base_price").asText());
                offer.put("price", content.path("base_price").asText());
                offer.put("additional_fee", "0.00");
                offer.put("currency", "CNY");
                offer.put("stock_status", "IN_STOCK");
                offer.put("valid_from", "2026-09-01T00:00:00Z");
                offer.put("valid_to", "2027-09-01T00:00:00Z");
                offer.put("version", 0);
                dataset.withArray("offers").add(offer);
            }
            var review = mapper.createObjectNode();
            review.put("review_id", "R-" + productId.substring(2) + "-001");
            review.put("product_id", productId);
            review.put("sku_id", productSkus.getFirst().path("sku_id").asText());
            review.put("rating", 4);
            review.put("text", content.path("review").asText());
            dataset.withArray("reviews").add(review);

            var evidence = mapper.createObjectNode();
            evidence.put("evidence_id", "EV-" + productId.substring(2) + "-001");
            evidence.put("product_id", productId);
            evidence.putNull("sku_id");
            evidence.put("category_id", product.path("category_id").asText());
            evidence.put("source_type", "EXPERT_SUMMARY");
            evidence.put("topic", content.path("topic").asText());
            evidence.put("sentiment", "MIXED");
            evidence.put("trust_level", 0.82);
            evidence.put("published_at", "2026-08-20T00:00:00Z");
            evidence.put("content", content.path("evidence").asText());
            evidence.put("is_simulated", true);
            dataset.withArray("knowledge_documents").add(evidence);
        }
        for (var collection : new String[] {
            "categories", "products", "skus", "shops", "offers", "reviews", "knowledge_documents"
        }) {
            dataset.withArray(collection).forEach(entity -> {
                ((ObjectNode) entity).put("dataset_version", datasetVersion);
                ((ObjectNode) entity).put("updated_at", updatedAt);
            });
        }
        return dataset;
    }

    private static void importCategories(Connection connection, JsonNode dataset, ObjectMapper mapper) throws Exception {
        var sql = """
                INSERT INTO categories (id, code, name, depth_level, schema_version, schema_json, status, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), schema_version = VALUES(schema_version),
                    schema_json = VALUES(schema_json), status = 'ACTIVE', updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var category : dataset.withArray("categories")) {
                var time = timestamp(category);
                statement.setString(1, category.path("category_id").asText());
                statement.setString(2, category.path("category_id").asText());
                statement.setString(3, category.path("name").asText());
                statement.setString(4, category.path("schema_version").asText());
                var schemaPath = locateProjectFile(CATEGORY_SCHEMA_DIRECTORY + "/"
                        + category.path("category_id").asText().toLowerCase(java.util.Locale.ROOT) + ".schema.json");
                var categorySchema = mapper.readTree(schemaPath.toFile());
                if (!category.path("schema_version").asText().equals(categorySchema.path("schemaVersion").asText())) {
                    throw new IllegalStateException("Dataset 与类目 Schema 版本不一致：" + category.path("category_id").asText());
                }
                statement.setString(5, mapper.writeValueAsString(categorySchema));
                statement.setTimestamp(6, time);
                statement.setTimestamp(7, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importProducts(Connection connection, JsonNode dataset, ObjectMapper mapper) throws Exception {
        var sql = """
                INSERT INTO products (id, category_id, brand, model, canonical_variant, display_name, subtitle,
                    canonical_specs_json, selling_points_json, limitation_json, warranty_summary, dataset_version,
                    is_simulated, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, '{}', '[]', '[]', NULL, ?, 1, 'ACTIVE', ?, ?)
                ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), brand = VALUES(brand), model = VALUES(model),
                    display_name = VALUES(display_name), dataset_version = VALUES(dataset_version), status = 'ACTIVE',
                    updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var product : dataset.withArray("products")) {
                var time = timestamp(product);
                var brand = product.path("brand").asText();
                var model = product.path("model").asText();
                statement.setString(1, product.path("product_id").asText());
                statement.setString(2, product.path("category_id").asText());
                statement.setString(3, brand);
                statement.setString(4, model);
                statement.setString(5, product.path("product_id").asText());
                statement.setString(6, brand + " " + model);
                statement.setString(7, product.path("dataset_version").asText());
                statement.setTimestamp(8, time);
                statement.setTimestamp(9, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importSkus(Connection connection, JsonNode dataset, ObjectMapper mapper) throws Exception {
        var sql = """
                INSERT INTO skus (id, product_id, sku_code, display_name, attributes_json, stock_status,
                    stock_quantity, warranty_months, dataset_version, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'IN_STOCK', 1, 12, ?, 'ACTIVE', ?, ?)
                ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), display_name = VALUES(display_name),
                    attributes_json = VALUES(attributes_json), dataset_version = VALUES(dataset_version),
                    status = 'ACTIVE', updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var sku : dataset.withArray("skus")) {
                var time = timestamp(sku);
                var id = sku.path("sku_id").asText();
                statement.setString(1, id);
                statement.setString(2, sku.path("product_id").asText());
                statement.setString(3, id);
                statement.setString(4, id);
                statement.setString(5, mapper.writeValueAsString(sku.path("attributes")));
                statement.setString(6, sku.path("dataset_version").asText());
                statement.setTimestamp(7, time);
                statement.setTimestamp(8, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importShops(Connection connection, JsonNode dataset) throws Exception {
        var sql = """
                INSERT INTO shops (id, name, dataset_version, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), dataset_version = VALUES(dataset_version),
                    status = 'ACTIVE', updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var shop : dataset.withArray("shops")) {
                var time = timestamp(shop);
                statement.setString(1, shop.path("shop_id").asText());
                statement.setString(2, shop.path("name").asText());
                statement.setString(3, shop.path("dataset_version").asText());
                statement.setTimestamp(4, time);
                statement.setTimestamp(5, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importOffers(Connection connection, JsonNode dataset) throws Exception {
        var sql = """
                INSERT INTO offers (id, sku_id, shop_id, list_price, sale_price, additional_fee, currency,
                    stock_status, valid_from, valid_to, dataset_version, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                ON DUPLICATE KEY UPDATE list_price = VALUES(list_price), sale_price = VALUES(sale_price),
                    additional_fee = VALUES(additional_fee), currency = VALUES(currency),
                    stock_status = VALUES(stock_status), valid_from = VALUES(valid_from), valid_to = VALUES(valid_to),
                    dataset_version = VALUES(dataset_version), status = 'ACTIVE', version = VALUES(version),
                    updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var offer : dataset.withArray("offers")) {
                var time = timestamp(offer);
                statement.setString(1, offer.path("offer_id").asText());
                statement.setString(2, offer.path("sku_id").asText());
                statement.setString(3, offer.path("shop_id").asText());
                statement.setBigDecimal(4, new BigDecimal(offer.path("list_price").asText()));
                statement.setBigDecimal(5, new BigDecimal(offer.path("price").asText()));
                statement.setBigDecimal(6, new BigDecimal(offer.path("additional_fee").asText()));
                statement.setString(7, offer.path("currency").asText());
                statement.setString(8, offer.path("stock_status").asText());
                statement.setObject(9, LocalDateTime.ofInstant(
                        Instant.parse(offer.path("valid_from").asText()), ZoneOffset.UTC));
                statement.setObject(10, LocalDateTime.ofInstant(
                        Instant.parse(offer.path("valid_to").asText()), ZoneOffset.UTC));
                statement.setString(11, offer.path("dataset_version").asText());
                statement.setLong(12, offer.path("version").asLong());
                statement.setTimestamp(13, time);
                statement.setTimestamp(14, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importReviews(Connection connection, JsonNode dataset) throws Exception {
        var sql = """
                INSERT INTO reviews (id, product_id, sku_id, rating, content, dataset_version, is_simulated, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                ON DUPLICATE KEY UPDATE rating = VALUES(rating), content = VALUES(content),
                    dataset_version = VALUES(dataset_version), updated_at = VALUES(updated_at)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var review : dataset.withArray("reviews")) {
                var time = timestamp(review);
                statement.setString(1, review.path("review_id").asText());
                statement.setString(2, review.path("product_id").asText());
                if (review.path("sku_id").isMissingNode()) statement.setNull(3, java.sql.Types.VARCHAR);
                else statement.setString(3, review.path("sku_id").asText());
                statement.setInt(4, review.path("rating").asInt());
                statement.setString(5, review.path("text").asText());
                statement.setString(6, review.path("dataset_version").asText());
                statement.setTimestamp(7, time);
                statement.setTimestamp(8, time);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void importKnowledgeDocuments(Connection connection, JsonNode dataset) throws Exception {
        var sql = """
                INSERT INTO knowledge_documents (id, evidence_id, product_id, sku_id, category_id, source_type,
                    topic, sentiment, trust_level, published_at, content, content_hash, embedding_model,
                    embedding_version, injection_flag, dataset_version, is_simulated, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'fixture-hash', 'fixture-hash-v1', ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), sku_id = VALUES(sku_id),
                    category_id = VALUES(category_id), source_type = VALUES(source_type), topic = VALUES(topic),
                    sentiment = VALUES(sentiment), trust_level = VALUES(trust_level),
                    published_at = VALUES(published_at), content = VALUES(content),
                    content_hash = VALUES(content_hash), embedding_model = VALUES(embedding_model),
                    embedding_version = VALUES(embedding_version), injection_flag = VALUES(injection_flag),
                    dataset_version = VALUES(dataset_version)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (var document : dataset.withArray("knowledge_documents")) {
                var evidenceId = document.path("evidence_id").asText();
                var content = normalizeContent(document.path("content").asText());
                statement.setString(1, evidenceId + "-C001");
                statement.setString(2, evidenceId);
                statement.setString(3, document.path("product_id").asText());
                if (document.path("sku_id").isNull()) statement.setNull(4, java.sql.Types.VARCHAR);
                else statement.setString(4, document.path("sku_id").asText());
                statement.setString(5, document.path("category_id").asText());
                statement.setString(6, document.path("source_type").asText());
                statement.setString(7, document.path("topic").asText());
                if (document.path("sentiment").isNull()) statement.setNull(8, java.sql.Types.VARCHAR);
                else statement.setString(8, document.path("sentiment").asText());
                statement.setBigDecimal(9, document.path("trust_level").decimalValue());
                statement.setObject(10, LocalDateTime.ofInstant(
                        Instant.parse(document.path("published_at").asText()), ZoneOffset.UTC));
                statement.setString(11, content);
                statement.setString(12, sha256(content));
                statement.setBoolean(13, PromptInjectionScanner.isSuspicious(content));
                statement.setString(14, document.path("dataset_version").asText());
                statement.setTimestamp(15, timestamp(document));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String normalizeContent(String content) {
        return content.trim().replaceAll("\\s+", " ");
    }

    private static String sha256(String content) throws Exception {
        var bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static Timestamp timestamp(JsonNode node) {
        return Timestamp.from(Instant.parse(node.path("updated_at").asText()));
    }

    private static Path locateDefaultDataset() {
        return locateProjectFile(DEFAULT_DATASET_RELATIVE_PATH);
    }

    private static Path locateProjectFile(String relativePath) {
        var workingDirectory = Path.of("").toAbsolutePath();
        for (var directory = workingDirectory; directory != null; directory = directory.getParent()) {
            var candidate = directory.resolve(relativePath);
            if (candidate.toFile().isFile()) return candidate;
        }
        throw new IllegalStateException("找不到项目文件：" + relativePath);
    }

    private static String requiredEnvironment(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + key);
        return value;
    }
}
