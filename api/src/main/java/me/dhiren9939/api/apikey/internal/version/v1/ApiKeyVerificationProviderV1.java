package me.dhiren9939.api.apikey.internal.version.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyVerificationFailure;
import me.dhiren9939.api.apikey.internal.ApiKeyToken;
import me.dhiren9939.api.apikey.internal.ApiKeyEntity;
import me.dhiren9939.api.apikey.internal.ApiKeyRepository;
import me.dhiren9939.api.apikey.internal.ApiKeyVerificationProvider;
import me.dhiren9939.api.apikey.internal.version.Sha256SecretHasher;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service("verificationV1")
@RequiredArgsConstructor
class ApiKeyVerificationProviderV1 implements ApiKeyVerificationProvider {
    public static final String SUPPORTED_KEY_VERSION = "V1";

    private final Clock clock;
    private final ApiKeyRepository apiKeyRepository;
    private final Sha256SecretHasher secretHasher;

    @Override
    public boolean supports(ApiKeyToken apiKeyToken) {
        return apiKeyToken.getKeyVersion().equalsIgnoreCase(SUPPORTED_KEY_VERSION);
    }

    @Override
    public ApiKeyToken verify(ApiKeyToken apiKeyToken) {
        ApiKeyEntity entity = apiKeyRepository.findByAccessId(apiKeyToken.getAccessId())
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId {}", apiKeyToken.getAccessId());
                    return new ApiKeyVerificationFailure("Could not verify the ApiKey");
                });

        byte[] presented = secretHasher.hash(apiKeyToken.getAccessSecret());
        byte[] stored = HexFormat.of().parseHex(entity.getAccessSecretHash());

        if (!MessageDigest.isEqual(presented, stored)) {
            log.warn("Secret mismatch for accessId {}", apiKeyToken.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now(clock))) {
            log.warn("ApiKey with accessId {} is expired", apiKeyToken.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        apiKeyToken.setApiKeyId(entity.getApiKeyId());
        apiKeyToken.setApiKeyName(entity.getApiKeyName());
        apiKeyToken.setUserId(entity.getUserId());
        apiKeyToken.setPermissions(entity.getPermissions());
        apiKeyToken.setCreatedAt(entity.getCreatedAt());
        apiKeyToken.setExpiresAt(entity.getExpiresAt());
        apiKeyToken.setVerified(true);

        return apiKeyToken;
    }
}
