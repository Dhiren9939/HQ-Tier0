package me.dhiren9939.backend.access.application.usecase.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.shared.access.ApiKeyVerificationFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Authenticates a presented credential. Unknown, provisioning, deleted, expired and wrong-secret keys,
 * and unsupported key formats, all fail identically so callers cannot tell them apart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class VerifyApiKeyHandler implements VerifyApiKeyUseCase {
    static final String FAILURE_MESSAGE = "Could not verify the ApiKey";

    private final ApiKeys apiKeys;
    private final List<CredentialVerifier> verifiers;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public VerifiedApiKey handle(VerifyApiKeyQuery query) {
        ParsedApiKey presented = ParsedApiKey.parse(query.accessId(), query.accessSecret());

        CredentialVerifier verifier = verifiers.stream()
                .filter(v -> v.supports(presented.keyFormat()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No credential verifier for key format {}", presented.keyFormat());
                    return failure();
                });

        ApiKey key = apiKeys.findByAccessId(presented.accessId())
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId={}", presented.accessId());
                    return failure();
                });

        if (!key.canAuthenticate(clock.instant())) {
            log.warn("ApiKey with accessId={} cannot authenticate in state {}", presented.accessId(), key.state());
            throw failure();
        }

        if (!verifier.matches(presented.accessSecret(), key.getAccessSecretHash())) {
            log.warn("Secret mismatch for accessId {}", presented.accessId());
            throw failure();
        }

        return new VerifiedApiKey(key.getApiKeyId(), key.getUserId(), key.getApiKeyName(),
                key.getPermissions(), key.getCreatedAt(), key.getExpiresAt());
    }

    private static ApiKeyVerificationFailure failure() {
        return new ApiKeyVerificationFailure(FAILURE_MESSAGE);
    }
}
