package com.hengpick.mall.recommendation.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.recommendation.domain.RecommendationReportRepository;
import com.hengpick.mall.recommendation.domain.RecommendationReportSnapshot;
import com.hengpick.mall.recommendation.domain.StoredRecommendationReport;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("database")
public class MyBatisRecommendationReportRepository implements RecommendationReportRepository {
    private final RecommendationReportMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisRecommendationReportRepository(
            RecommendationReportMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredRecommendationReport> findCurrent(String userId, String sessionId) {
        return Optional.ofNullable(mapper.findCurrent(userId, sessionId)).map(this::map);
    }

    @Override
    @Transactional
    public void publishInitial(StoredRecommendationReport report) {
        var weights = report.snapshot().recommendation().context().weights();
        if (mapper.claimInitialVersion(report.userId(), report.sessionId(), write(weights), report.createdAt()) != 1) {
            throw new IllegalArgumentException("决策任务不存在或已有报告");
        }
        insert(report);
    }

    @Override
    @Transactional
    public boolean appendReweighted(StoredRecommendationReport report, int expectedVersion) {
        var weights = report.snapshot().recommendation().context().weights();
        if (mapper.advanceVersion(report.userId(), report.sessionId(), expectedVersion, report.version(),
                write(weights), report.createdAt()) != 1) {
            return false;
        }
        insert(report);
        return true;
    }

    private void insert(StoredRecommendationReport report) {
        mapper.insertReport(report.sessionId(), report.version(), report.selectedSkuId(), write(report.report()),
                write(report.snapshot()), write(report.versions()), report.createdAt());
    }

    private StoredRecommendationReport map(RecommendationReportRow row) {
        return new StoredRecommendationReport(row.sessionId(), row.userId(), row.version(), row.selectedSkuId(),
                readMap(row.reportJson()), readMap(row.versionsJson()),
                read(row.recommendationSnapshotJson(), RecommendationReportSnapshot.class), row.createdAt());
    }

    private Map<String, Object> readMap(String value) {
        return read(value, new TypeReference<>() {});
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("推荐快照无法解析", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("推荐报告无法解析", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("推荐报告无法序列化", exception);
        }
    }
}
