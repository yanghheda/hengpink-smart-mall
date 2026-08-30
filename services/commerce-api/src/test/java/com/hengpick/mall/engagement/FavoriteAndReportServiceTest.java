package com.hengpick.mall.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hengpick.mall.engagement.application.EngagementService;
import com.hengpick.mall.engagement.domain.Favorite;
import com.hengpick.mall.engagement.domain.FavoriteType;
import com.hengpick.mall.engagement.domain.HistoricalReport;
import com.hengpick.mall.engagement.domain.EngagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FavoriteAndReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private InMemoryRepository repository;
    private EngagementService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        var sequence = new AtomicInteger();
        service = new EngagementService(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "01KTESTFAVORITE" + String.format("%011d", sequence.incrementAndGet()));
        repository.products.add("PRODUCT-1");
        repository.reports.add(report("USER-1", "SESSION-1", 2));
    }

    @Test
    void duplicateProductFavoriteReturnsTheExistingResource() {
        var first = service.addFavorite("USER-1", FavoriteType.PRODUCT, "PRODUCT-1", null);
        var duplicate = service.addFavorite("USER-1", FavoriteType.PRODUCT, "PRODUCT-1", null);

        assertThat(duplicate).isEqualTo(first);
        assertThat(repository.favorites).hasSize(1);
        assertThat(first.snapshot()).isEmpty();
    }

    @Test
    void reportFavoriteBindsTheRequestedHistoricalVersionInsteadOfTheLatestVersion() {
        repository.reports.add(report("USER-1", "SESSION-1", 3));

        var favorite = service.addFavorite("USER-1", FavoriteType.REPORT, "SESSION-1", 2);

        assertThat(favorite.entityId()).isEqualTo("SESSION-1:2");
        assertThat(favorite.snapshot()).containsEntry("reportVersion", 2);
        assertThat(favorite.snapshot()).containsEntry("selectedSkuId", "SKU-2");
        assertThat(favorite.snapshot()).containsEntry("summary", "第二版报告正文");
    }

    @Test
    void cannotFavoriteAnotherUsersReportAndDoesNotRevealWhetherItExists() {
        assertThatThrownBy(() -> service.addFavorite("USER-2", FavoriteType.REPORT, "SESSION-1", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("收藏目标不存在");
    }

    @Test
    void deletingReportRemovesOriginalTextAndSanitizesFavoriteSnapshot() {
        var favorite = service.addFavorite("USER-1", FavoriteType.REPORT, "SESSION-1", 2);

        service.deleteHistoricalReport("USER-1", "SESSION-1", 2);

        assertThat(repository.findReport("USER-1", "SESSION-1", 2)).isEmpty();
        var sanitized = repository.findFavorite("USER-1", FavoriteType.REPORT, favorite.entityId()).orElseThrow();
        assertThat(sanitized.snapshot()).containsOnlyKeys(
                "sessionId", "reportVersion", "selectedSkuId", "datasetVersion", "scoringVersion");
        assertThat(sanitized.snapshot()).doesNotContainValue("第二版报告正文");
        assertThat(repository.deletionAudits).containsExactly("USER-1:DECISION_REPORT:SESSION-1:2");
    }

    @Test
    void deletingFavoriteIsIdempotentAndUserScoped() {
        var favorite = service.addFavorite("USER-1", FavoriteType.PRODUCT, "PRODUCT-1", null);

        service.deleteFavorite("USER-2", favorite.id());
        assertThat(repository.favorites).hasSize(1);

        service.deleteFavorite("USER-1", favorite.id());
        service.deleteFavorite("USER-1", favorite.id());
        assertThat(repository.favorites).isEmpty();
    }

    private HistoricalReport report(String userId, String sessionId, int version) {
        return new HistoricalReport(sessionId, userId, version, "SKU-" + version,
                Map.of("summary", version == 2 ? "第二版报告正文" : "第三版报告正文"),
                Map.of("datasetVersion", "dataset-v1", "scoringVersion", "score-v1"), NOW.minusSeconds(60));
    }

    private static final class InMemoryRepository implements EngagementRepository {
        private final List<String> products = new ArrayList<>();
        private final List<HistoricalReport> reports = new ArrayList<>();
        private final List<Favorite> favorites = new ArrayList<>();
        private final List<String> deletionAudits = new ArrayList<>();

        @Override
        public boolean productExists(String productId) {
            return products.contains(productId);
        }

        @Override
        public Optional<HistoricalReport> findReport(String userId, String sessionId, int version) {
            return reports.stream().filter(item -> item.userId().equals(userId)
                    && item.sessionId().equals(sessionId) && item.version() == version).findFirst();
        }

        @Override
        public Optional<Favorite> findFavorite(String userId, FavoriteType type, String entityId) {
            return favorites.stream().filter(item -> item.userId().equals(userId)
                    && item.entityType() == type && item.entityId().equals(entityId)).findFirst();
        }

        @Override
        public boolean insertFavorite(Favorite favorite) {
            if (findFavorite(favorite.userId(), favorite.entityType(), favorite.entityId()).isPresent()) {
                return false;
            }
            favorites.add(favorite);
            return true;
        }

        @Override
        public List<Favorite> findFavorites(String userId, FavoriteType type) {
            return favorites.stream().filter(item -> item.userId().equals(userId)
                    && (type == null || item.entityType() == type)).toList();
        }

        @Override
        public void deleteFavorite(String userId, String favoriteId) {
            favorites.removeIf(item -> item.userId().equals(userId) && item.id().equals(favoriteId));
        }

        @Override
        public void deleteReportAndSanitizeFavorites(
                String userId, String sessionId, int version, Map<String, Object> sanitizedSnapshot) {
            reports.removeIf(item -> item.userId().equals(userId)
                    && item.sessionId().equals(sessionId) && item.version() == version);
            var entityId = sessionId + ":" + version;
            for (int index = 0; index < favorites.size(); index++) {
                var item = favorites.get(index);
                if (item.userId().equals(userId) && item.entityType() == FavoriteType.REPORT
                        && item.entityId().equals(entityId)) {
                    favorites.set(index, new Favorite(item.id(), item.userId(), item.entityType(), item.entityId(),
                            new LinkedHashMap<>(sanitizedSnapshot), item.createdAt()));
                }
            }
        }

        @Override
        public void recordDeletion(String userId, String resourceType, String resourceId) {
            deletionAudits.add(userId + ":" + resourceType + ":" + resourceId);
        }
    }
}
