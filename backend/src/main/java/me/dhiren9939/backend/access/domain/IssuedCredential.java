package me.dhiren9939.backend.access.domain;

/**
 * Carries a newly generated access identifier, one-time secret, and secret hash.
 */
public record IssuedCredential(String accessId, String accessSecret, String secretHash) {
}
