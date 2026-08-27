package com.hengpick.mall.catalog.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class CommerceDatasetImporter {
    private static final String DEFAULT_DATASET_RELATIVE_PATH = "packages/commerce-dataset/fixtures/curated/commerce-demo-2026.08.1.json";

    private CommerceDatasetImporter() {}

    public static void main(String[] args) throws Exception {
        var datasetPath = args.length == 0 ? locateDefaultDataset() : Path.of(args[0]);
        var url = requiredEnvironment("MYSQL_URL");
        var username = requiredEnvironment("MYSQL_USERNAME");
        var password = requiredEnvironment("MYSQL_PASSWORD");
        var mapper = new ObjectMapper();
        var dataset = mapper.readTree(datasetPath.toFile());

        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                importCategories(connection, dataset, mapper);
                importProducts(connection, dataset, mapper);
                importSkus(connection, dataset, mapper);
                importShops(connection, dataset);
                importOffers(connection, dataset);
                importReviews(connection, dataset);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        System.out.printf("已导入 Dataset：%s（%s）%n", dataset.path("dataset_version").asText(), datasetPath);
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
                statement.setString(5, mapper.writeValueAsString(category));
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

    private static Timestamp timestamp(JsonNode node) {
        return Timestamp.from(Instant.parse(node.path("updated_at").asText()));
    }

    private static Path locateDefaultDataset() {
        var workingDirectory = Path.of("").toAbsolutePath();
        for (var directory = workingDirectory; directory != null; directory = directory.getParent()) {
            var candidate = directory.resolve(DEFAULT_DATASET_RELATIVE_PATH);
            if (candidate.toFile().isFile()) return candidate;
        }
        throw new IllegalStateException("找不到固定 Dataset：" + DEFAULT_DATASET_RELATIVE_PATH);
    }

    private static String requiredEnvironment(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + key);
        return value;
    }
}
