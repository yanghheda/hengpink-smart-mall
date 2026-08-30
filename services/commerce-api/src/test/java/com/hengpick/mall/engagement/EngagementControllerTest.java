package com.hengpick.mall.engagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hengpick.mall.engagement.application.EngagementService;
import com.hengpick.mall.engagement.domain.EngagementRepository;
import com.hengpick.mall.engagement.domain.Favorite;
import com.hengpick.mall.engagement.domain.FavoriteType;
import com.hengpick.mall.engagement.domain.HistoricalReport;
import com.hengpick.mall.engagement.web.EngagementController;
import com.hengpick.mall.engagement.web.EngagementExceptionHandler;
import com.hengpick.mall.identity.infrastructure.JwtH5AccessTokenVerifier;
import com.hengpick.mall.identity.infrastructure.JwtH5SessionTokenIssuer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EngagementControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final String SECRET = "engagement-test-secret-must-be-32-bytes";
    private MockMvc mockMvc;
    private String authorization;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var repository = new InMemoryRepository();
        var service = new EngagementService(repository, clock, () -> "FAVORITE-1");
        var verifier = new JwtH5AccessTokenVerifier(SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(new EngagementController(service, verifier, clock))
                .setControllerAdvice(new EngagementExceptionHandler()).build();
        authorization = "Bearer " + new JwtH5SessionTokenIssuer(SECRET)
                .issue("USER-1", "DEMO_USER", NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    @Test
    void reportFavoriteReturnsTheBoundVersionAndRepeatedRequestIsIdempotent() throws Exception {
        var body = "{\"entityType\":\"REPORT\",\"entityId\":\"SESSION-1\",\"reportVersion\":2}";
        mockMvc.perform(post("/api/v1/favorites").header("Authorization", authorization)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("FAVORITE-1"))
                .andExpect(jsonPath("$.data.entityId").value("SESSION-1:2"))
                .andExpect(jsonPath("$.data.snapshot.reportVersion").value(2));
        mockMvc.perform(post("/api/v1/favorites").header("Authorization", authorization)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("FAVORITE-1"));
    }

    @Test
    void deletingReportMakesHistoryUnavailableWithoutExposingItsText() throws Exception {
        mockMvc.perform(delete("/api/v1/decision-sessions/SESSION-1/reports/2")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(get("/api/v1/decision-sessions/SESSION-1/reports/2")
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ENGAGEMENT_RESOURCE_NOT_FOUND"));
    }

    private static final class InMemoryRepository implements EngagementRepository {
        private final List<Favorite> favorites = new ArrayList<>();
        private HistoricalReport report = new HistoricalReport("SESSION-1", "USER-1", 2, "SKU-2",
                Map.of("summary", "历史正文"), Map.of("datasetVersion", "d1", "scoringVersion", "s1"), NOW);

        @Override public boolean productExists(String productId) { return "PRODUCT-1".equals(productId); }
        @Override public Optional<HistoricalReport> findReport(String userId, String sessionId, int version) {
            return report != null && report.userId().equals(userId) && report.sessionId().equals(sessionId)
                    && report.version() == version ? Optional.of(report) : Optional.empty();
        }
        @Override public Optional<Favorite> findFavorite(String userId, FavoriteType type, String entityId) {
            return favorites.stream().filter(item -> item.userId().equals(userId)
                    && item.entityType() == type && item.entityId().equals(entityId)).findFirst();
        }
        @Override public boolean insertFavorite(Favorite favorite) {
            if (findFavorite(favorite.userId(), favorite.entityType(), favorite.entityId()).isPresent()) return false;
            favorites.add(favorite);
            return true;
        }
        @Override public List<Favorite> findFavorites(String userId, FavoriteType type) { return List.copyOf(favorites); }
        @Override public void deleteFavorite(String userId, String favoriteId) {
            favorites.removeIf(item -> item.userId().equals(userId) && item.id().equals(favoriteId));
        }
        @Override public void deleteReportAndSanitizeFavorites(
                String userId, String sessionId, int version, Map<String, Object> sanitizedSnapshot) {
            report = null;
        }
        @Override public void recordDeletion(String userId, String resourceType, String resourceId) {}
    }
}
