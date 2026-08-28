package com.hengpick.mall.identity.domain;

public record RefreshSession(String sessionId, UserAccount user, String refreshTokenHash) {}
