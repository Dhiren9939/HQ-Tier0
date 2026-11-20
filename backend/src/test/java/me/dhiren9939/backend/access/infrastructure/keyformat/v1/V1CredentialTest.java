package me.dhiren9939.backend.access.infrastructure.keyformat.v1;

import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import me.dhiren9939.backend.access.infrastructure.keyformat.Sha256SecretHasher;
import me.dhiren9939.backend.access.infrastructure.keyformat.SortableEncoder;
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
    void everyIssuedCredentialHasTheV1Shape() {
        for (int i = 0; i < 1_000; i++) {
            IssuedCredential credential = issuer.issue();

            assertThat(credential.accessId()).hasSize(25);
            assertThat(credential.accessSecret()).hasSize(43);
            assertThat(verifier.isWellFormed(credential.accessId(), credential.accessSecret())).isTrue();
        }
    }

    @Test
    void theV1ShapeIsDerivedFromTheEncoding() {
        assertThat(V1KeyShape.ACCESS_ID_LENGTH).isEqualTo(25);
        assertThat(V1KeyShape.SECRET_LENGTH).isEqualTo(43);
        assertThat(SortableEncoder.encodedLength(2)).isEqualTo(22);
        assertThat(SortableEncoder.encodedLength(4)).isEqualTo(43);
    }

    @Test
    void theVerifierRefusesAnythingElseAsMalformed() {
        IssuedCredential good = issuer.issue();
        String id = good.accessId();
        String secret = good.accessSecret();

        assertThat(verifier.isWellFormed(id + "a", secret)).as("access id too long").isFalse();
        assertThat(verifier.isWellFormed(id.substring(0, 24), secret)).as("access id too short").isFalse();
        assertThat(verifier.isWellFormed(id, secret + "a")).as("secret too long").isFalse();
        assertThat(verifier.isWellFormed(id, secret.substring(1))).as("secret too short").isFalse();
        assertThat(verifier.isWellFormed("V2_" + id.substring(3), secret)).as("other prefix").isFalse();
        assertThat(verifier.isWellFormed(id.substring(0, 24) + "\n", secret)).as("line break").isFalse();
        assertThat(verifier.isWellFormed(id, secret.substring(0, 42) + "!")).as("outside the alphabet").isFalse();
        assertThat(verifier.isWellFormed(id, "\u0000".repeat(43))).as("control characters").isFalse();
        assertThat(verifier.isWellFormed("V1_" + "a".repeat(1_000_000), "s".repeat(1_000_000))).as("huge").isFalse();
    }

    @Test
    void theVerifierSupportsV1CaseInsensitivelyAndNothingElse() {
        assertThat(verifier.supports("V1")).isTrue();
        assertThat(verifier.supports("v1")).isTrue();
        assertThat(verifier.supports("V2")).isFalse();
    }
}
