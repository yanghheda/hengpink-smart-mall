package com.hengpick.mall.catalog.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommerceDatasetImporterTest {
    @Test
    void rangeClosureCompositionMatchesThePublishedFiveCategoryDataset() throws Exception {
        var mapper = new ObjectMapper();
        var projectRoot = Path.of("../..");
        var base = mapper.readTree(projectRoot.resolve(
                "packages/commerce-dataset/fixtures/curated/commerce-demo-2026.09.1.json").toFile());
        var expansion = mapper.readTree(projectRoot.resolve(
                "packages/commerce-dataset/fixtures/curated/p15-s02-range-closure.json").toFile());

        var dataset = CommerceDatasetImporter.mergeRangeClosure(mapper, base, expansion);

        assertThat(dataset.path("dataset_version").asText()).isEqualTo("commerce-demo-2026.09.2");
        assertThat(dataset.withArray("categories").size()).isEqualTo(5);
        assertThat(dataset.withArray("products").size()).isEqualTo(20);
        assertThat(dataset.withArray("skus").size()).isEqualTo(40);
        assertThat(dataset.withArray("offers").size()).isEqualTo(40);
        dataset.withArray("offers").forEach(offer ->
                assertThat(offer.path("valid_to").asText()).isEqualTo("2027-09-01T00:00:00Z"));
        dataset.withArray("categories").forEach(category ->
                assertThat(category.path("schema_coverage").asText()).isEqualTo("DEEP"));
        dataset.withArray("categories").forEach(category -> {
            assertThat(category.path("confidence_policy").path("deep_threshold").asDouble()).isEqualTo(0.6);
            assertThat(category.path("confidence_policy").path("fallback_max_level").asText()).isEqualTo("MEDIUM");
        });
    }
}
