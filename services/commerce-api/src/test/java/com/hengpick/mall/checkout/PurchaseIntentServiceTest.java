package com.hengpick.mall.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.checkout.application.PricePlanStaleException;
import com.hengpick.mall.checkout.application.PurchaseIntentService;
import com.hengpick.mall.checkout.domain.CheckoutRepository;
import com.hengpick.mall.checkout.domain.CurrentPricePlan;
import com.hengpick.mall.checkout.domain.PurchaseIntent;
import com.hengpick.mall.checkout.domain.PurchaseIntentStatus;
import com.hengpick.mall.checkout.domain.ReportSelection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurchaseIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private InMemoryRepository repository;
    private PurchaseIntentService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        repository.selection = new ReportSelection("SESSION-1", 2, "SKU-1", "OFFER-1:BASE",
                new BigDecimal("2999.00"));
        service = new PurchaseIntentService(repository,
                (skuId, pricePlanId) -> new CurrentPricePlan(pricePlanId, skuId, "OFFER-1",
                        new BigDecimal("2999.00"), "CNY", "dataset-v1", 3),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "INTENT-1");
    }

    @Test
    void 价格发生变化时拒绝创建且不保存快照() {
        service = new PurchaseIntentService(repository,
                (skuId, pricePlanId) -> new CurrentPricePlan(pricePlanId, skuId, "OFFER-1",
                        new BigDecimal("3099.00"), "CNY", "dataset-v1", 4),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> "INTENT-1");

        assertThatThrownBy(() -> service.create("USER-1", "SESSION-1", 2, "SKU-1", "OFFER-1:BASE", "KEY-1"))
                .isInstanceOf(PricePlanStaleException.class);
        assertThat(repository.intents).isEmpty();
    }

    @Test
    void 创建只冻结服务端价格事实且相同幂等键返回同一资源() {
        var first = service.create("USER-1", "SESSION-1", 2, "SKU-1", "OFFER-1:BASE", "KEY-1");
        var repeated = service.create("USER-1", "SESSION-1", 2, "SKU-1", "OFFER-1:BASE", "KEY-1");

        assertThat(repeated).isEqualTo(first);
        assertThat(repository.intents).hasSize(1);
        assertThat(first.pricePlanSnapshot().finalPrice()).isEqualByComparingTo("2999.00");
        assertThat(first.status()).isEqualTo(PurchaseIntentStatus.CREATED);
    }

    @Test
    void 其他用户无法读取且重复确认不产生额外副作用() {
        service.create("USER-1", "SESSION-1", 2, "SKU-1", "OFFER-1:BASE", "KEY-1");

        assertThatThrownBy(() -> service.get("USER-2", "INTENT-1"))
                .isInstanceOf(IllegalArgumentException.class);
        var first = service.confirm("USER-1", "INTENT-1");
        var repeated = service.confirm("USER-1", "INTENT-1");

        assertThat(first.status()).isEqualTo(PurchaseIntentStatus.CONFIRMED);
        assertThat(repeated).isEqualTo(first);
        assertThat(repository.confirmWrites).isEqualTo(1);
    }

    private static final class InMemoryRepository implements CheckoutRepository {
        private ReportSelection selection;
        private final List<PurchaseIntent> intents = new ArrayList<>();
        private int confirmWrites;

        @Override public Optional<ReportSelection> findReportSelection(
                String userId, String sessionId, int reportVersion, String skuId) {
            return "USER-1".equals(userId) && selection.sessionId().equals(sessionId)
                    && selection.reportVersion() == reportVersion && selection.skuId().equals(skuId)
                    ? Optional.of(selection) : Optional.empty();
        }
        @Override public Optional<PurchaseIntent> findByIdempotencyKey(String userId, String key) {
            return intents.stream().filter(item -> item.userId().equals(userId)
                    && item.idempotencyKey().equals(key)).findFirst();
        }
        @Override public Optional<PurchaseIntent> findOwned(String userId, String id) {
            return intents.stream().filter(item -> item.userId().equals(userId) && item.id().equals(id)).findFirst();
        }
        @Override public void insert(PurchaseIntent intent) { intents.add(intent); }
        @Override public void update(PurchaseIntent intent) {
            intents.replaceAll(item -> item.id().equals(intent.id()) ? intent : item);
            confirmWrites++;
        }
    }
}
