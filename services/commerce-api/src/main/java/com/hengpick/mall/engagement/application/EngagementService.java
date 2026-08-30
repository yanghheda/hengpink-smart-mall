package com.hengpick.mall.engagement.application;

import com.hengpick.mall.engagement.domain.EngagementRepository;
import com.hengpick.mall.engagement.domain.Favorite;
import com.hengpick.mall.engagement.domain.FavoriteType;
import com.hengpick.mall.engagement.domain.HistoricalReport;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;

public class EngagementService {
    private final EngagementRepository repository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public EngagementService(EngagementRepository repository, Clock clock, Supplier<String> idGenerator) {
        this.repository = repository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Favorite addFavorite(String userId, FavoriteType type, String entityId, Integer reportVersion) {
        requireText(entityId, "entityId");
        var normalizedId = normalizedEntityId(type, entityId, reportVersion);
        var existing = repository.findFavorite(userId, type, normalizedId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        var snapshot = snapshot(userId, type, entityId, reportVersion);
        var favorite = new Favorite(idGenerator.get(), userId, type, normalizedId, snapshot, clock.instant());
        if (repository.insertFavorite(favorite)) {
            return favorite;
        }
        return repository.findFavorite(userId, type, normalizedId).orElseThrow();
    }

    public List<Favorite> listFavorites(String userId, FavoriteType type) {
        return repository.findFavorites(userId, type);
    }

    public void deleteFavorite(String userId, String favoriteId) {
        repository.deleteFavorite(userId, favoriteId);
        repository.recordDeletion(userId, "FAVORITE", favoriteId);
    }

    public HistoricalReport getHistoricalReport(String userId, String sessionId, int version) {
        return repository.findReport(userId, sessionId, version)
                .orElseThrow(() -> new IllegalArgumentException("历史报告不存在"));
    }

    @Transactional
    public void deleteHistoricalReport(String userId, String sessionId, int version) {
        var report = getHistoricalReport(userId, sessionId, version);
        repository.deleteReportAndSanitizeFavorites(userId, sessionId, version, sanitizedSnapshot(report));
        repository.recordDeletion(userId, "DECISION_REPORT", sessionId + ":" + version);
    }

    private Map<String, Object> snapshot(
            String userId, FavoriteType type, String entityId, Integer reportVersion) {
        if (type == FavoriteType.PRODUCT) {
            if (!repository.productExists(entityId)) {
                throw new IllegalArgumentException("收藏目标不存在");
            }
            return Map.of();
        }
        if (reportVersion == null || reportVersion < 1) {
            throw new IllegalArgumentException("报告收藏必须提供有效 reportVersion");
        }
        var report = repository.findReport(userId, entityId, reportVersion)
                .orElseThrow(() -> new IllegalArgumentException("收藏目标不存在"));
        var snapshot = new LinkedHashMap<>(sanitizedSnapshot(report));
        snapshot.putAll(report.report());
        return Map.copyOf(snapshot);
    }

    private Map<String, Object> sanitizedSnapshot(HistoricalReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("sessionId", report.sessionId());
        snapshot.put("reportVersion", report.version());
        snapshot.put("selectedSkuId", report.selectedSkuId());
        snapshot.put("datasetVersion", report.versions().get("datasetVersion"));
        snapshot.put("scoringVersion", report.versions().get("scoringVersion"));
        return Map.copyOf(snapshot);
    }

    private String normalizedEntityId(FavoriteType type, String entityId, Integer reportVersion) {
        if (type == FavoriteType.REPORT) {
            if (reportVersion == null || reportVersion < 1) {
                throw new IllegalArgumentException("报告收藏必须提供有效 reportVersion");
            }
            return entityId + ":" + reportVersion;
        }
        return entityId;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
