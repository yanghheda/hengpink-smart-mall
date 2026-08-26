package com.hengpick.mall.catalog.web;

/** 成功响应的统一信封。 */
public record ApiEnvelope<T>(
        /*
         * 服务端生成的请求标识。
         */
        String requestId,
        /*
         * 业务响应数据。
         */
        T data,
        /*
         * 响应元数据。
         */
        Meta meta) {
    public static <T> ApiEnvelope<T> success(T data, java.time.Instant serverTime) {
        return new ApiEnvelope<>(java.util.UUID.randomUUID().toString(), data, new Meta(serverTime.toString()));
    }

    /** 成功响应的元数据。 */
    public record Meta(
            /*
             * 服务端生成响应的 UTC 时间，ISO 8601 格式。
             */
            String serverTime) {}
}
