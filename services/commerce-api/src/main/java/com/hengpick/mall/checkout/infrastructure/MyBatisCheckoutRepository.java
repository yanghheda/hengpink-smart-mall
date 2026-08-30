package com.hengpick.mall.checkout.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.checkout.domain.CheckoutRepository;
import com.hengpick.mall.checkout.domain.CurrentPricePlan;
import com.hengpick.mall.checkout.domain.PurchaseIntent;
import com.hengpick.mall.checkout.domain.PurchaseIntentStatus;
import com.hengpick.mall.checkout.domain.ReportSelection;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class MyBatisCheckoutRepository implements CheckoutRepository {
    private final CheckoutMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisCheckoutRepository(CheckoutMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ReportSelection> findReportSelection(
            String userId, String sessionId, int reportVersion, String skuId) {
        var row = mapper.findReport(userId, sessionId, reportVersion);
        if (row == null) return Optional.empty();
        try {
            var recommendations = objectMapper.readTree(row.reportJson()).path("recommendations");
            for (var item : recommendations) {
                if (skuId.equals(item.path("skuId").asText())) {
                    return Optional.of(new ReportSelection(sessionId, reportVersion, skuId,
                            item.path("pricePlanId").asText(), item.path("finalPrice").decimalValue()));
                }
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("报告价格快照无法解析", exception);
        }
    }

    @Override public Optional<PurchaseIntent> findByIdempotencyKey(String userId, String key) {
        return Optional.ofNullable(mapper.findByKey(userId, key)).map(this::toDomain);
    }
    @Override public Optional<PurchaseIntent> findOwned(String userId, String id) {
        return Optional.ofNullable(mapper.findOwned(userId, id)).map(this::toDomain);
    }
    @Override public void insert(PurchaseIntent intent) {
        mapper.insert(intent.id(), intent.userId(), intent.sessionId(), intent.reportVersion(), intent.skuId(),
                write(intent.pricePlanSnapshot()), intent.status().name(),
                intent.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime(),
                intent.createdAt().atOffset(ZoneOffset.UTC).toLocalDateTime(), intent.idempotencyKey());
    }
    @Override public void update(PurchaseIntent intent) {
        mapper.update(intent.id(), intent.userId(), intent.status().name(),
                intent.confirmedAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    private PurchaseIntent toDomain(PurchaseIntentRow row) {
        try {
            return new PurchaseIntent(row.id(), row.userId(), row.sessionId(), row.reportVersion(), row.skuId(),
                    objectMapper.readValue(row.snapshotJson(), CurrentPricePlan.class),
                    PurchaseIntentStatus.valueOf(row.status()), row.expiresAt().toInstant(ZoneOffset.UTC),
                    row.createdAt().toInstant(ZoneOffset.UTC),
                    row.confirmedAt() == null ? null : row.confirmedAt().toInstant(ZoneOffset.UTC),
                    row.idempotencyKey());
        } catch (Exception exception) {
            throw new IllegalStateException("购买意向快照无法解析", exception);
        }
    }
    private String write(CurrentPricePlan value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("购买意向快照无法序列化", exception); }
    }
}
