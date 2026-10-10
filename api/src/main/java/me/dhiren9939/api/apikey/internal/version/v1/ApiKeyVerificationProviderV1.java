package me.dhiren9939.api.apikey.internal.version.v1;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.ApiKeyVerificationFailure;
import me.dhiren9939.api.apikey.internal.ApiKey;
import me.dhiren9939.api.apikey.internal.ApiKeyEntity;
import me.dhiren9939.api.apikey.internal.ApiKeyRepository;
import me.dhiren9939.api.apikey.internal.ApiKeyVerificationProvider;
import me.dhiren9939.api.apikey.internal.version.Sha256SecretHasher;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service("verificationV1")
@RequiredArgsConstructor
class ApiKeyVerificationProviderV1 implements ApiKeyVerificationProvider {

    private static final String SUPPORTED_KEY_VERSION = "V1";
    private final ApiKeyRepository apiKeyRepository;
    private final Sha256SecretHasher secretHasher;

    @Override
    public boolean supports(ApiKey apiKey) {
        return apiKey.getKeyVersion().equalsIgnoreCase(SUPPORTED_KEY_VERSION);
    }

    @Override
    public ApiKey verify(ApiKey apiKey) {
        ApiKeyEntity entity = apiKeyRepository.findByAccessId(apiKey.getAccessId())
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId {}", apiKey.getAccessId());
                    return new ApiKeyVerificationFailure("Could not verify the ApiKey");
                });

        byte[] presented = secretHasher.hash(apiKey.getAccessSecret());
        byte[] stored = HexFormat.of().parseHex(entity.getAccessSecretHash());

        if (!MessageDigest.isEqual(presented, stored)) {
            log.warn("Secret mismatch for accessId {}", apiKey.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
            log.warn("ApiKey with accessId {} is expired", apiKey.getAccessId());
            throw new ApiKeyVerificationFailure("Could not verify the ApiKey");
        }

        apiKey.setApiKeyId(entity.getApiKeyId());
        apiKey.setUserId(entity.getUserId());
        apiKey.setIngestionPermission(entity.getIngestionPermission());
        apiKey.setTenantPermission(entity.getTenantPermission());
        apiKey.setChannelPermission(entity.getChannelPermission());
        apiKey.setCreatedAt(entity.getCreatedAt());
        apiKey.setExpiresAt(entity.getExpiresAt());
        apiKey.setVerified(true);

        return apiKey;
    }
}
