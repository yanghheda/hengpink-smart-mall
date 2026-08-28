package com.hengpick.mall.identity.domain;

@FunctionalInterface
public interface TokenDigester {
    String digest(String token);
}
