package me.dhiren9939.api.apikey.service;

import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.github.f4b6a3.uuid.UuidCreator;
import org.hibernate.id.uuid.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Generates and hashes API key secrets - kept separate from ApiKeyService so whatever later
 * authenticates incoming API keys (hash the presented key, look it up by hash) can reuse the
 * same hashing logic instead of depending on the create/patch/delete service. */
@Component
public class ApiKeySecretGenerator {

    public String generate() {
        return "hq-" + UuidCreator.getTimeOrderedEpoch();
    }

    public String hash(String secret) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
