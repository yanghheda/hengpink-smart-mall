package com.hengpick.mall.decision.web;

import com.hengpick.mall.decision.application.DecisionCallbackService;
import com.hengpick.mall.decision.domain.AgentStepCallback;
import com.hengpick.mall.decision.domain.RunCompletionCallback;
import com.hengpick.mall.integration.agent.CallbackTokenCodec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 接收 Python 的受信任内部回调，不向外部客户端暴露。 */
@RestController
@Profile("database")
@RequestMapping("/internal/v1/decision-runs/{runId}")
public class AgentCallbackController {
    private final DecisionCallbackService service;
    private final CallbackTokenCodec tokenCodec;
    private final Clock clock;

    public AgentCallbackController(DecisionCallbackService service, CallbackTokenCodec tokenCodec, Clock clock) {
        this.service = service;
        this.tokenCodec = tokenCodec;
        this.clock = clock;
    }

    @PostMapping("/steps")
    public ResponseEntity<Map<String, String>> appendStep(
            @PathVariable String runId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody StepRequest request) {
        requireCallbackToken(authorization, runId);
        service.appendStep(request.toDomain(runId));
        return ResponseEntity.ok(Map.of("status", "ACCEPTED"));
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, String>> complete(
            @PathVariable String runId,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CompleteRequest request) {
        requireCallbackToken(authorization, runId);
        var outcome = service.complete(request.toDomain(runId));
        return ResponseEntity.ok(Map.of("status", outcome.name()));
    }

    private void requireCallbackToken(String authorization, String runId) {
        var token = authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!tokenCodec.permits(token, runId, clock.instant())) {
            throw new InvalidCallbackTokenException();
        }
    }

    public record StepRequest(
            /* Run 版本，用于隔离迟到回调。 */
            @Min(1) int runVersion,
            /* 同一 Run 内单调递增的 Step 序号。 */
            @Min(1) int sequence,
            /* Agent 节点稳定代码。 */
            @NotBlank String node,
            /* Step 执行状态。 */
            @NotBlank String status,
            /* Step 开始时间，使用 UTC ISO 8601。 */
            @NotNull Instant startedAt,
            /* Step 完成时间，使用 UTC ISO 8601。 */
            @NotNull Instant completedAt,
            /* 规范化回调体的 SHA-256。 */
            @NotBlank String contentHash,
            /* 不含用户原文和 Prompt 的输入摘要。 */
            @NotNull Map<String, Object> inputSummary,
            /* 不含大字段和隐式推理的输出摘要。 */
            @NotNull Map<String, Object> outputSummary) {
        AgentStepCallback toDomain(String runId) {
            return new AgentStepCallback(runId, runVersion, sequence, node, status, startedAt, completedAt,
                    contentHash, inputSummary, outputSummary);
        }
    }

    public record CompleteRequest(
            /* Run 版本，用于判断是否仍为 Session 当前版本。 */
            @Min(1) int runVersion,
            /* 完成类型，如 REPORT_READY 或 FAILED。 */
            @NotBlank String completionType,
            /* 规范化完成回调体的 SHA-256。 */
            @NotBlank String contentHash,
            /* Stub 的结构化结果摘要，不含最终金额和最终评分。 */
            @NotNull Map<String, Object> resultSummary,
            /* Agent 完成时间，使用 UTC ISO 8601。 */
            @NotNull Instant completedAt) {
        RunCompletionCallback toDomain(String runId) {
            return new RunCompletionCallback(runId, runVersion, completionType, contentHash, resultSummary,
                    completedAt);
        }
    }
}
