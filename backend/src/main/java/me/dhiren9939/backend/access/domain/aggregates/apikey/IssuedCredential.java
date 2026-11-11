package me.dhiren9939.backend.access.domain.aggregates.apikey;

/**
 * Carries a newly generated access identifier, one-time secret, and secret hash.
 */
public record IssuedCredential(String accessId, String accessSecret, String secretHash) {
}
