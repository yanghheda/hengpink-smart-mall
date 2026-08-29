package com.hengpick.mall.integration.agent;

import java.util.List;
import java.util.Map;

/** Java 发给 Python 的版本化 Agent Run 请求。 */
public record AgentRunRequest(
        String runId,
        String sessionId,
        int runVersion,
        Map<String, String> versions,
        Map<String, Object> input,
        Callback callback,
        Budget budget) {
    public static AgentRunRequest stub(
            String runId, String sessionId, int runVersion, String datasetVersion, String callbackToken) {
        return new AgentRunRequest(
                runId,
                sessionId,
                runVersion,
                Map.of("dataset", datasetVersion),
                Map.of("messages", List.of(), "memorySnapshot", List.of()),
                new Callback("commerce-api-internal", callbackToken),
                new Budget(12_000, 20_000, 5));
    }

    /** 回调地址使用服务端配置标识，避免请求方注入任意 URL。 */
    public record Callback(String baseUrlId, String callbackToken) {}

    /** Stub 也接收正式协议预算，后续 Graph 可直接复用。 */
    public record Budget(int softTimeoutMs, int hardTimeoutMs, int maxModelCalls) {}
}
