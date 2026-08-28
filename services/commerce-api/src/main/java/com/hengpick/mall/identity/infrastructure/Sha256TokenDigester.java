package com.hengpick.mall.identity.infrastructure;

import com.hengpick.mall.identity.domain.TokenDigester;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256TokenDigester implements TokenDigester {
    @Override
    public String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行时不支持 SHA-256", exception);
        }
    }
}
