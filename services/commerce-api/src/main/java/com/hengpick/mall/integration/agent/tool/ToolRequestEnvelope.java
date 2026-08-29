package com.hengpick.mall.integration.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

/** Python 调用内部 Commerce Tool 时使用的统一请求信封。 */
public record ToolRequestEnvelope(
        /* 当前 Agent Run 标识。 */
        String runId,
        /* 当前 Session 内不可回退的 Run 版本。 */
        int runVersion,
        /* 单次 Tool 调用的幂等标识。 */
        String toolCallId,
        /* 本轮绑定的商品数据集版本。 */
        String datasetVersion,
        /* 调用方允许的超时毫秒数。 */
        int timeoutMs,
        /* 由具体 Tool 继续校验的结构化输入。 */
        JsonNode input) {}
