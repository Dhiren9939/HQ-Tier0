package me.dhiren9939.api.auth.jwt;

import java.util.UUID;

/** Decoded, verified contents of an access token - what a request filter actually needs. */
public record AccessTokenClaims(UUID userId, String email) {
}
