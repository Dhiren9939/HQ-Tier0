package me.dhiren9939.backend.access.application.usecase.service.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKey;
import me.dhiren9939.backend.access.domain.repositories.ApiKeys;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates a presented credential. Unknown, expired and wrong-secret keys, and unsupported key formats,
 * all fail identically so callers cannot tell them apart.
 *
 * <p>The credential is relayed from outside, so it is untrusted: the key format's exact shape
 * ({@link CredentialVerifier#isWellFormed}) is checked before the lookup and the hash, which bounds it.</p>
 *
 * <p>Every failure is logged as one line with a fixed message and structured key-value fields: {@code event},
 * {@code reason} (the code the caller gets), {@code cause} (why, for operators only) and the sanitized
 * {@code accessId}. The caller's input is never part of the message, and every identifier goes through
 * {@link LoggedIdentifier}, so a crafted credential cannot forge log lines or make them long.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
class VerifyApiKeyHandler implements VerifyApiKeyUseCase {
    static final String FAILED_EVENT = "apikey.verify.failed";
    static final String FAILED_MESSAGE = "API key verification failed";

    private final ApiKeys apiKeys;
    private final List<CredentialVerifier> verifiers;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public VerifiedApiKey handle(VerifyApiKeyQuery query) {
        ParsedApiKey presented = parse(query);

        CredentialVerifier verifier = verifiers.stream()
                .filter(v -> v.supports(presented.keyFormat()))
                .findFirst()
                .orElseThrow(() -> logged("unsupported_key_format", presented, ApiKeyVerificationFailure.invalid()));

        if (!verifier.isWellFormed(presented.accessId(), presented.accessSecret()))
            throw malformed(Optional.of(presented.accessId()), Optional.of(presented.accessSecret()));

        ApiKey key = apiKeys.findByAccessId(presented.accessId())
                .orElseThrow(() -> logged("unknown_access_id", presented, ApiKeyVerificationFailure.invalid()));

        // The secret comes first: the more specific outcomes below are only for someone who holds it, so an
        // unknown access id and a wrong secret stay indistinguishable and nothing about a key leaks without it.
        if (!verifier.matches(presented.accessSecret(), key.getAccessSecretHash()))
            throw logged("secret_mismatch", presented, ApiKeyVerificationFailure.invalid());

        Instant now = clock.instant();
        if (!key.canAuthenticate(now))
            throw logged("key_expired", presented, ApiKeyVerificationFailure.expired());

        return new VerifiedApiKey(key.getApiKeyId(), key.getUserId(), key.getApiKeyName(),
                key.getPermissions(), key.getCreatedAt(), key.getExpiresAt());
    }

    /** Reads the key format from the Access ID; a missing part or no format prefix is malformed. */
    private static ParsedApiKey parse(VerifyApiKeyQuery query) {
        Optional<String> accessId = Optional.ofNullable(query).map(VerifyApiKeyQuery::accessId);
        Optional<String> accessSecret = Optional.ofNullable(query).map(VerifyApiKeyQuery::accessSecret);
        try {
            return ParsedApiKey.parse(accessId.orElse(null), accessSecret.orElse(null));
        } catch (ApiKeyVerificationFailure failure) {
            throw malformed(accessId, accessSecret);
        }
    }

    /** Logs a malformed credential by its sanitized id and the lengths only, and returns the failure to throw. */
    private static ApiKeyVerificationFailure malformed(Optional<String> accessId, Optional<String> accessSecret) {
        ApiKeyVerificationFailure failure = ApiKeyVerificationFailure.malformed();
        LoggingEventBuilder event = failureEvent("malformed_credential", failure)
                .addKeyValue("accessId", accessId.map(LoggedIdentifier::of).orElse("<none>"));
        accessId.ifPresent(id -> event.addKeyValue("accessIdLength", id.length()));
        accessSecret.ifPresent(secret -> event.addKeyValue("accessSecretLength", secret.length()));
        event.log(FAILED_MESSAGE);
        return failure;
    }

    /** Logs why a well-formed credential was refused and returns the failure to throw. */
    private static ApiKeyVerificationFailure logged(String cause, ParsedApiKey presented,
                                                    ApiKeyVerificationFailure failure) {
        failureEvent(cause, failure)
                .addKeyValue("accessId", LoggedIdentifier.of(presented.accessId()))
                .addKeyValue("keyFormat", LoggedIdentifier.of(presented.keyFormat()))
                .log(FAILED_MESSAGE);
        return failure;
    }

    /** Every failure is a WARN: callers can trigger each of them at will, so none is an operator alert. */
    private static LoggingEventBuilder failureEvent(String cause, ApiKeyVerificationFailure failure) {
        return log.atLevel(Level.WARN)
                .addKeyValue("event", FAILED_EVENT)
                .addKeyValue("reason", failure.code())
                .addKeyValue("cause", cause);
    }
}
