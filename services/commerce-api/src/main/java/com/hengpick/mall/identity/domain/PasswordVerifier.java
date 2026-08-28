package com.hengpick.mall.identity.domain;

@FunctionalInterface
public interface PasswordVerifier {
    boolean matches(String plainPassword, String passwordHash);
}
