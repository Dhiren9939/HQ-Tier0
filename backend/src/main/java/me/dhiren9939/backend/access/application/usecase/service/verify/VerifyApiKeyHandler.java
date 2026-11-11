package me.dhiren9939.backend.access.application.usecase.service.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.shared.access.ApiKeyVerificationFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Authenticates a presented credential. Unknown, provisioning, deleted, expired and wrong-secret keys,
 * and unsupported key formats, all fail identically so callers cannot tell them apart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class VerifyApiKeyHandler implements VerifyApiKeyUseCase {
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
                    return ApiKeyVerificationFailure.invalid();
                });

        ApiKey key = apiKeys.findByAccessId(presented.accessId())
                .orElseThrow(() -> {
                    log.warn("No ApiKey found for accessId={}", presented.accessId());
                    return ApiKeyVerificationFailure.invalid();
                });

        // The secret comes first: the more specific outcomes below are only for someone who holds it, so an
        // unknown access id and a wrong secret stay indistinguishable and nothing about a key leaks without it.
        if (!verifier.matches(presented.accessSecret(), key.getAccessSecretHash())) {
            log.warn("Secret mismatch for accessId {}", presented.accessId());
            throw ApiKeyVerificationFailure.invalid();
        }

        Instant now = clock.instant();
        if (!key.canAuthenticate(now)) {
            ApiKeyState state = key.state(now);
            log.warn("ApiKey with accessId={} cannot authenticate in state {}", presented.accessId(), state);
            throw switch (state) {
                case EXPIRED -> ApiKeyVerificationFailure.expired();
                case DELETED -> ApiKeyVerificationFailure.deleted();
                default -> ApiKeyVerificationFailure.notActive();
            };
        }

        return new VerifiedApiKey(key.getApiKeyId(), key.getUserId(), key.getApiKeyName(),
                key.getPermissions(), key.getCreatedAt(), key.getExpiresAt());
    }
}
