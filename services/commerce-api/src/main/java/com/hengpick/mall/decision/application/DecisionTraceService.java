package com.hengpick.mall.decision.application;

import com.hengpick.mall.decision.domain.DecisionTraceRepository;
import com.hengpick.mall.decision.domain.DecisionTraceSnapshot;
import com.hengpick.mall.identity.application.ObjectAccessGuard;
import com.hengpick.mall.identity.domain.OwnedObject;
import com.hengpick.mall.identity.domain.RequestSubject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校验管理员与对象归属，并输出字段白名单内的 Trace。 */
public class DecisionTraceService {
    private static final Set<String> INPUT_KEYS = Set.of("categoryId", "candidateCount", "toolName", "toolCallId",
            "sourceVersion", "requestHash", "topic", "skuId", "productId");
    private static final Set<String> OUTPUT_KEYS = Set.of("resultCount", "evidenceIds", "factIds", "toolName",
            "toolCallId", "sourceVersion", "status", "retryCount", "errorCode", "recallScores");
    private final DecisionTraceRepository repository;
    private final ObjectAccessGuard accessGuard;

    public DecisionTraceService(DecisionTraceRepository repository, ObjectAccessGuard accessGuard) {
        this.repository = repository;
        this.accessGuard = accessGuard;
    }

    public DecisionTraceSnapshot getTrace(RequestSubject subject, String runId) {
        var snapshot = repository.findByRunId(runId).orElseThrow(DecisionTraceNotFoundException::new);
        accessGuard.requireTraceAccess(subject, new OwnedObject("DECISION_RUN", runId, snapshot.ownerId()));
        var steps = snapshot.steps().stream().map(step -> new DecisionTraceSnapshot.Step(step.sequence(), step.node(),
                step.status(), step.startedAt(), step.completedAt(), step.durationMs(), step.errorCode(),
                List.copyOf(step.warningCodes()), allow(step.inputSummary(), INPUT_KEYS),
                allow(step.outputSummary(), OUTPUT_KEYS))).toList();
        return new DecisionTraceSnapshot(snapshot.runId(), snapshot.sessionId(), snapshot.ownerId(),
                snapshot.runVersion(), snapshot.status(), snapshot.activeNode(), snapshot.failureCode(),
                List.copyOf(snapshot.degradationCodes()), snapshot.traceId(), snapshot.startedAt(),
                snapshot.completedAt(), snapshot.modelVersion(), snapshot.promptVersion(), snapshot.datasetVersion(),
                snapshot.scoringVersion(), snapshot.pricingRuleVersion(), snapshot.embeddingVersion(),
                snapshot.tokenInput(), snapshot.tokenOutput(), snapshot.estimatedCost(), steps);
    }

    private Map<String, Object> allow(Map<String, Object> source, Set<String> keys) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (keys.contains(key)) result.put(key, value);
        });
        return Map.copyOf(result);
    }
}
