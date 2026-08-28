package com.hengpick.mall.identity.domain;

@FunctionalInterface
public interface RefreshTokenGenerator {
    String generate();
}
