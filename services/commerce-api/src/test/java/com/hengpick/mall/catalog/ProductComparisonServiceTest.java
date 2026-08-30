package com.hengpick.mall.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.catalog.application.ProductComparisonService;
import com.hengpick.mall.catalog.domain.CategoryComparisonSchema;
import com.hengpick.mall.catalog.domain.ComparisonCandidate;
import com.hengpick.mall.catalog.domain.ComparisonMode;
import com.hengpick.mall.catalog.domain.ProductComparisonPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductComparisonServiceTest {
    private static final CategoryComparisonSchema PHONE_SCHEMA = new CategoryComparisonSchema(
            "PHONE",
            "phone-1.0",
            List.of(
                    new CategoryComparisonSchema.Attribute("batteryMah", "电池容量", "mAh", true),
                    new CategoryComparisonSchema.Attribute("storageGb", "存储容量", "GB", true),
                    new CategoryComparisonSchema.Attribute("color", "颜色", null, false)));

    @Test
    void differencesOnlyKeepsChangedComparableAttributesInSchemaOrder() {
        var service = service(List.of(
                candidate("S-1", "PHONE", Map.of("batteryMah", 5000, "storageGb", 256)),
                candidate("S-2", "PHONE", Map.of("batteryMah", 4800, "storageGb", 256))));

        var result = service.compare(List.of("S-1", "S-2"), ComparisonMode.DIFFERENCES);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().attributeKey()).isEqualTo("batteryMah");
        assertThat(result.rows().getFirst().values()).containsExactly(5000, 4800);
    }

    @Test
    void differencesModeOnlyKeepsDifferencesRelatedToCurrentNeed() {
        var service = service(List.of(
                candidate("S-1", "PHONE", Map.of("batteryMah", 5000, "storageGb", 256)),
                candidate("S-2", "PHONE", Map.of("batteryMah", 4800, "storageGb", 128))));

        var result = service.compare(
                List.of("S-1", "S-2"), ComparisonMode.DIFFERENCES, List.of("batteryMah"));

        assertThat(result.rows()).extracting(row -> row.attributeKey()).containsExactly("batteryMah");
    }

    @Test
    void allModeUsesUnknownForMissingValuesInsteadOfGuessing() {
        var service = service(List.of(
                candidate("S-1", "PHONE", Map.of("batteryMah", 5000, "storageGb", 256)),
                candidate("S-2", "PHONE", Map.of("storageGb", 128))));

        var result = service.compare(List.of("S-1", "S-2"), ComparisonMode.ALL);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().values()).containsExactly(5000, "未知");
    }

    @Test
    void rejectsCandidatesFromDifferentCategories() {
        var service = service(List.of(
                candidate("S-1", "PHONE", Map.of()),
                candidate("S-2", "MONITOR", Map.of())));

        assertThatThrownBy(() -> service.compare(List.of("S-1", "S-2"), ComparisonMode.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一类目");
    }

    @Test
    void rejectsCountOutsideTwoToFourAndDuplicateSku() {
        var service = service(List.of(candidate("S-1", "PHONE", Map.of())));

        assertThatThrownBy(() -> service.compare(List.of("S-1"), ComparisonMode.ALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.compare(List.of("S-1", "S-1"), ComparisonMode.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    private ProductComparisonService service(List<ComparisonCandidate> candidates) {
        ProductComparisonPort port = new ProductComparisonPort() {
            @Override
            public List<ComparisonCandidate> findCandidates(List<String> skuIds) {
                return candidates;
            }

            @Override
            public CategoryComparisonSchema findSchema(String categoryId) {
                return PHONE_SCHEMA;
            }
        };
        return new ProductComparisonService(port);
    }

    private ComparisonCandidate candidate(String skuId, String categoryId, Map<String, Object> attributes) {
        return new ComparisonCandidate("P-" + skuId, skuId, "商品 " + skuId, categoryId, attributes);
    }
}
