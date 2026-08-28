package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureTokenGenerator implements RefreshTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
