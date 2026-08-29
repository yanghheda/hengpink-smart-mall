package com.hengpick.mall.integration.agent.tool;

import java.time.Instant;
import java.util.List;

/** 内部 Commerce Tool 的统一响应信封。 */
public record ToolResponseEnvelope(
        /* SUCCESS 或 FAILED。 */
        String status,
        /* 具体 Tool 的结构化结果。 */
        Object data,
        /* 结果实际读取的数据集版本。 */
        String sourceVersion,
        /* 服务端生成结果的 UTC 时刻。 */
        Instant updatedAt,
        /* 确定性业务结果的完整度，范围 0 到 1。 */
        double confidence,
        /* 不阻断使用但必须可见的警告代码。 */
        List<String> warnings,
        /* 失败时稳定的机器错误码。 */
        String errorCode) {

    public static ToolResponseEnvelope success(Object data, String sourceVersion, Instant updatedAt) {
        return new ToolResponseEnvelope(
                "SUCCESS", data, sourceVersion, updatedAt, 1.0, List.of(), null);
    }
}
