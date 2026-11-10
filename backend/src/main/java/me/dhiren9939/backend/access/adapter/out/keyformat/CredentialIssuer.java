package me.dhiren9939.backend.access.adapter.out.keyformat;

import me.dhiren9939.backend.access.domain.IssuedCredential;

/**
 * Generates credentials in one key format. Deliberately not an {@code IssueCredentialPort}:
 * configuration picks which issuer is the current one and exposes that single port bean.
 */
public interface CredentialIssuer {
    /** The key format this issuer produces, e.g. {@code V1}. */
    String keyFormat();

    IssuedCredential issue();
}
