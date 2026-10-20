package me.dhiren9939.api.apikey.internal.version.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyVerificationFailure;
import me.dhiren9939.api.apikey.internal.ApiKeyToken;
import me.dhiren9939.api.apikey.internal.ApiKeyDto;
import me.dhiren9939.api.apikey.internal.ApiKeyPersistenceService;
import me.dhiren9939.api.apikey.internal.ApiKeyVerificationProvider;
import me.dhiren9939.api.apikey.internal.version.Sha256SecretHasher;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.NoSuchElementException;

@Slf4j
@Service("verificationV1")
@RequiredArgsConstructor
class ApiKeyVerificationProviderV1 implements ApiKeyVerificationProvider {
    public static final String SUPPORTED_KEY_VERSION = "V1";

    private final Clock clock;
    private final ApiKeyPersistenceService persistenceService;
    private final Sha256SecretHasher secretHasher;

    @Override
    public boolean supports(ApiKeyToken apiKeyToken) {
        return apiKeyToken.getKeyVersion().equalsIgnoreCase(SUPPORTED_KEY_VERSION);
    }

    @Override
    public ApiKeyToken verify(ApiKeyToken apiKeyToken) {
        ApiKeyDto apiKey;
        try {
            apiKey = persistenceService.getForVerification(apiKeyToken.getAccessId());
        } catch (NoSuchElementException exception) {
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        byte[] presented = secretHasher.hash(apiKeyToken.getAccessSecret());
        byte[] stored = HexFormat.of().parseHex(apiKey.accessSecretHash());

        if (!MessageDigest.isEqual(presented, stored)) {
            log.warn("Secret mismatch for accessId {}", apiKeyToken.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        if (apiKey.expiresAt() != null && apiKey.expiresAt().isBefore(Instant.now(clock))) {
            log.warn("ApiKey with accessId {} is expired", apiKeyToken.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        apiKeyToken.setApiKeyId(apiKey.apiKeyId());
        apiKeyToken.setApiKeyName(apiKey.apiKeyName());
        apiKeyToken.setUserId(apiKey.userId());
        apiKeyToken.setPermissions(apiKey.permissions());
        apiKeyToken.setCreatedAt(apiKey.createdAt());
        apiKeyToken.setExpiresAt(apiKey.expiresAt());
        apiKeyToken.setVerified(true);

        return apiKeyToken;
    }
}
