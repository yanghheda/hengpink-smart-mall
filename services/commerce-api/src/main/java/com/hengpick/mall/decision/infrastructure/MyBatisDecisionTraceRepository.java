package com.hengpick.mall.decision.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengpick.mall.decision.domain.DecisionTraceRepository;
import com.hengpick.mall.decision.domain.DecisionTraceSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 从 Decision 权威表组装只读 Trace 快照。 */
@Repository
@Profile("database")
public class MyBatisDecisionTraceRepository implements DecisionTraceRepository {
    private final DecisionMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisDecisionTraceRepository(DecisionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DecisionTraceSnapshot> findByRunId(String runId) {
        var run = mapper.findTraceRun(runId);
        if (run == null) return Optional.empty();
        var steps = mapper.findTraceSteps(runId).stream().map(row -> new DecisionTraceSnapshot.Step(row.sequence(),
                row.node(), row.status(), row.startedAt(), row.completedAt(), row.durationMs(), row.errorCode(),
                strings(row.warningCodesJson()), object(row.inputSummaryJson()), object(row.outputSummaryJson())))
                .toList();
        return Optional.of(new DecisionTraceSnapshot(run.runId(), run.sessionId(), run.ownerId(), run.runVersion(),
                run.status(), run.activeNode(), run.failureCode(), strings(run.degradationCodesJson()), run.traceId(),
                run.startedAt(), run.completedAt(), run.modelVersion(), run.promptVersion(), run.datasetVersion(),
                run.scoringVersion(), run.pricingRuleVersion(), run.embeddingVersion(), run.tokenInput(),
                run.tokenOutput(), run.estimatedCost(), steps));
    }

    private List<String> strings(String json) {
        if (json == null) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Trace 列表摘要无法解析", exception); }
    }

    private Map<String, Object> object(String json) {
        if (json == null) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Trace 对象摘要无法解析", exception); }
    }
}
