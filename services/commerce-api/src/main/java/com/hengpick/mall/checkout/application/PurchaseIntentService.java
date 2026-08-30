package com.hengpick.mall.checkout.application;

import com.hengpick.mall.checkout.domain.CheckoutRepository;
import com.hengpick.mall.checkout.domain.CurrentPricePlanPort;
import com.hengpick.mall.checkout.domain.PurchaseIntent;
import com.hengpick.mall.checkout.domain.PurchaseIntentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** 编排价格重确认、快照创建与模拟确认。 */
public final class PurchaseIntentService {
    private static final Duration INTENT_TTL = Duration.ofMinutes(30);
    private final CheckoutRepository repository;
    private final CurrentPricePlanPort pricePort;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public PurchaseIntentService(CheckoutRepository repository, CurrentPricePlanPort pricePort, Clock clock,
            Supplier<String> idGenerator) {
        this.repository = repository;
        this.pricePort = pricePort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public PurchaseIntent create(String userId, String sessionId, int reportVersion, String skuId,
            String pricePlanId, String idempotencyKey) {
        requireText(idempotencyKey, "幂等键");
        var existing = repository.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            var value = existing.orElseThrow();
            if (!value.sessionId().equals(sessionId) || value.reportVersion() != reportVersion
                    || !value.skuId().equals(skuId)
                    || !value.pricePlanSnapshot().pricePlanId().equals(pricePlanId)) {
                throw new IllegalArgumentException("幂等键已用于不同请求");
            }
            return value;
        }
        var selection = repository.findReportSelection(userId, sessionId, reportVersion, skuId)
                .orElseThrow(() -> new IllegalArgumentException("报告选择不存在"));
        if (!Objects.equals(selection.pricePlanId(), pricePlanId)) throw new PricePlanStaleException();
        var current = pricePort.revalidate(skuId, pricePlanId);
        if (!Objects.equals(current.skuId(), skuId) || !Objects.equals(current.pricePlanId(), pricePlanId)
                || selection.finalPrice().compareTo(current.finalPrice()) != 0) {
            throw new PricePlanStaleException();
        }
        var now = clock.instant();
        var intent = new PurchaseIntent(idGenerator.get(), userId, sessionId, reportVersion, skuId, current,
                PurchaseIntentStatus.CREATED, now.plus(INTENT_TTL), now, null, idempotencyKey);
        repository.insert(intent);
        return intent;
    }

    public PurchaseIntent get(String userId, String id) {
        return repository.findOwned(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("购买意向不存在"));
    }

    public PurchaseIntent confirm(String userId, String id) {
        var current = get(userId, id);
        if (current.status() == PurchaseIntentStatus.CONFIRMED) return current;
        var confirmed = current.confirm(clock.instant());
        repository.update(confirmed);
        return confirmed;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
    }
}
