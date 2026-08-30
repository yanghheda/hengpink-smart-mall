package com.hengpick.mall.engagement.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EngagementRepository {
    boolean productExists(String productId);

    Optional<HistoricalReport> findReport(String userId, String sessionId, int version);

    Optional<Favorite> findFavorite(String userId, FavoriteType type, String entityId);

    boolean insertFavorite(Favorite favorite);

    List<Favorite> findFavorites(String userId, FavoriteType type);

    void deleteFavorite(String userId, String favoriteId);

    void deleteReportAndSanitizeFavorites(
            String userId, String sessionId, int version, Map<String, Object> sanitizedSnapshot);

    void recordDeletion(String userId, String resourceType, String resourceId);
}
