package me.dhiren9939.backend.access.infrastructure.keyformat;

import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;

/**
 * Generates credentials in one key format. Deliberately not an {@code IssueCredentialPort}:
 * configuration picks which issuer is the current one and exposes that single {@code IssueCredentialPort} bean.
 */
public interface CredentialIssuer {
    /** The key format this issuer produces, e.g. {@code V1}. */
    String keyFormat();

    IssuedCredential issue();
}
