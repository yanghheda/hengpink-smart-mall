package com.hengpick.mall.integration.agent;

import java.time.Instant;

/** 签发并校验绑定到单个 Run 的短期回调凭证。 */
public interface CallbackTokenCodec {
    String issue(String runId, Instant issuedAt, Instant expiresAt);

    boolean permits(String token, String runId, Instant now);
}
