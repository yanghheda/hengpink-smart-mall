package com.hengpick.mall.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hengpick.mall.catalog.application.CatalogSearchService;
import com.hengpick.mall.catalog.domain.AttributeConstraint;
import com.hengpick.mall.catalog.domain.CatalogSearchCandidate;
import com.hengpick.mall.catalog.domain.CatalogSearchCriteria;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogSearchServiceTest {
    private final CatalogSearchService service = new CatalogSearchService(categoryId -> List.of(
            new CatalogSearchCandidate("P-1", "S-128", "手机甲 128GB", "PHONE", new BigDecimal("2899.00"),
                    "IN_STOCK", 2, Map.of("storageGb", 128, "batteryMah", 5000)),
            new CatalogSearchCandidate("P-1", "S-256", "手机甲 256GB", "PHONE", new BigDecimal("3299.00"),
                    "IN_STOCK", 2, Map.of("storageGb", 256, "batteryMah", 5000))));

    @Test
    void budgetAndStorageCombinationCanExplainZeroCandidates() {
        var criteria = new CatalogSearchCriteria("PHONE", null, new BigDecimal("3000.00"), true,
                List.of(new AttributeConstraint("storageGb", ">=", 256)));

        var result = service.search(criteria);

        assertEquals(0, result.matched().size());
        assertEquals(List.of("ATTRIBUTE_CONSTRAINT_FAILED"), result.rejected().get(0).reasonCodes());
        assertEquals(List.of("BUDGET_EXCEEDED"), result.rejected().get(1).reasonCodes());
    }

    @Test
    void unsupportedOperatorIsRejectedInsteadOfIgnored() {
        var criteria = new CatalogSearchCriteria("PHONE", null, null, false,
                List.of(new AttributeConstraint("storageGb", "<=", 256)));

        assertThrows(IllegalArgumentException.class, () -> service.search(criteria));
    }
}
