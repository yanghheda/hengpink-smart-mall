package com.hengpick.mall.engagement.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.engagement.domain.EngagementRepository;
import com.hengpick.mall.engagement.domain.Favorite;
import com.hengpick.mall.engagement.domain.FavoriteType;
import com.hengpick.mall.engagement.domain.HistoricalReport;
import com.hengpick.mall.identity.domain.DeletionAuditRecord;
import com.hengpick.mall.identity.domain.DeletionAuditRepository;
import com.hengpick.mall.identity.domain.TokenDigester;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class MyBatisEngagementRepository implements EngagementRepository {
    private final EngagementMapper mapper;
    private final ObjectMapper objectMapper;
    private final DeletionAuditRepository auditRepository;
    private final TokenDigester tokenDigester;
    private final Clock clock;

    public MyBatisEngagementRepository(
            EngagementMapper mapper,
            ObjectMapper objectMapper,
            DeletionAuditRepository auditRepository,
            TokenDigester tokenDigester,
            Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.auditRepository = auditRepository;
        this.tokenDigester = tokenDigester;
        this.clock = clock;
    }

    @Override
    public boolean productExists(String productId) {
        return mapper.productExists(productId);
    }

    @Override
    public Optional<HistoricalReport> findReport(String userId, String sessionId, int version) {
        return Optional.ofNullable(mapper.findReport(userId, sessionId, version)).map(this::report);
    }

    @Override
    public Optional<Favorite> findFavorite(String userId, FavoriteType type, String entityId) {
        return Optional.ofNullable(mapper.findFavorite(userId, type.name(), entityId)).map(this::favorite);
    }

    @Override
    public boolean insertFavorite(Favorite favorite) {
        return mapper.insertFavorite(favorite, write(favorite.snapshot())) == 1;
    }

    @Override
    public List<Favorite> findFavorites(String userId, FavoriteType type) {
        return mapper.findFavorites(userId, type == null ? null : type.name()).stream().map(this::favorite).toList();
    }

    @Override
    public void deleteFavorite(String userId, String favoriteId) {
        mapper.deleteFavorite(userId, favoriteId);
    }

    @Override
    public void deleteReportAndSanitizeFavorites(
            String userId, String sessionId, int version, Map<String, Object> sanitizedSnapshot) {
        if (mapper.deleteReport(userId, sessionId, version) != 1) {
            throw new IllegalArgumentException("历史报告不存在");
        }
        mapper.sanitizeReportFavorite(userId, sessionId + ":" + version, write(sanitizedSnapshot));
    }

    @Override
    public void recordDeletion(String userId, String resourceType, String resourceId) {
        auditRepository.record(new DeletionAuditRecord("DELETE", tokenDigester.digest(userId), resourceType,
                tokenDigester.digest(resourceId), clock.instant()));
    }

    private Favorite favorite(FavoriteRow row) {
        return new Favorite(row.id(), row.userId(), FavoriteType.valueOf(row.entityType()), row.entityId(),
                read(row.snapshotJson()), row.createdAt());
    }

    private HistoricalReport report(HistoricalReportRow row) {
        return new HistoricalReport(row.sessionId(), row.userId(), row.version(), row.selectedSkuId(),
                read(row.reportJson()), read(row.versionsJson()), row.createdAt());
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("收藏快照无法序列化", exception);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("收藏快照无法解析", exception);
        }
    }
}
