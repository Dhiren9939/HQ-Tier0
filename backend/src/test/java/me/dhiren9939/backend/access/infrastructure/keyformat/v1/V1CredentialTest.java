package me.dhiren9939.backend.access.infrastructure.keyformat.v1;

import me.dhiren9939.backend.access.infrastructure.keyformat.Sha256SecretHasher;
import me.dhiren9939.backend.access.infrastructure.keyformat.SortableEncoder;
import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class V1CredentialTest {

    private final Sha256SecretHasher hasher = new Sha256SecretHasher();
    private final V1CredentialIssuer issuer = new V1CredentialIssuer(new SortableEncoder(), new SecureRandom(), hasher);
    private final V1CredentialVerifier verifier = new V1CredentialVerifier(hasher);

    @Test
    void issuedCredentialsCarryTheFormatPrefixAndAHashOfTheSecret() {
        IssuedCredential credential = issuer.issue();

        assertThat(issuer.keyFormat()).isEqualTo("V1");
        assertThat(credential.accessId()).startsWith("V1_");
        assertThat(credential.accessSecret()).isNotBlank().isNotEqualTo(credential.secretHash());
        assertThat(credential.secretHash()).isEqualTo(hasher.hashHex(credential.accessSecret()));
    }

    @Test
    void everyIssuedCredentialIsDifferent() {
        IssuedCredential first = issuer.issue();
        IssuedCredential second = issuer.issue();

        assertThat(first.accessId()).isNotEqualTo(second.accessId());
        assertThat(first.accessSecret()).isNotEqualTo(second.accessSecret());
    }

    @Test
    void theVerifierAcceptsTheRightSecretAndRejectsAnyOther() {
        IssuedCredential credential = issuer.issue();

        assertThat(verifier.matches(credential.accessSecret(), credential.secretHash())).isTrue();
        assertThat(verifier.matches("wrong", credential.secretHash())).isFalse();
    }

    @Test
    void aMalformedStoredHashNeverMatchesInsteadOfThrowing() {
        assertThat(verifier.matches("anything", "not-hex")).isFalse();
    }

    @Test
    void theVerifierSupportsV1CaseInsensitivelyAndNothingElse() {
        assertThat(verifier.supports("V1")).isTrue();
        assertThat(verifier.supports("v1")).isTrue();
        assertThat(verifier.supports("V2")).isFalse();
    }
}
