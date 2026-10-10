package me.dhiren9939.api.apikey.internal.version;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class Sha256SecretHasher {

    public byte[] hash(String accessSecret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(accessSecret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public String hashHex(String accessSecret) {
        return HexFormat.of().formatHex(hash(accessSecret));
    }
}
