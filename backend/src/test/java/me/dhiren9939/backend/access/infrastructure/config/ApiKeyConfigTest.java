package me.dhiren9939.backend.access.infrastructure.config;

import me.dhiren9939.backend.access.infrastructure.keyformat.CredentialIssuer;
import me.dhiren9939.backend.access.application.interfaces.IssueCredentialPort;
import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyConfigTest {

    private static CredentialIssuer issuerFor(String format) {
        return new CredentialIssuer() {
            @Override
            public String keyFormat() {
                return format;
            }

            @Override
            public IssuedCredential issue() {
                return new IssuedCredential(format + "_id", "secret", "hash");
            }
        };
    }

    private final ApiKeyConfig config = new ApiKeyConfig();
    private final List<CredentialIssuer> issuers = List.of(issuerFor("V1"), issuerFor("V2"));

    @Test
    void newCredentialsUseTheConfiguredKeyFormat() {
        IssueCredentialPort port = config.issueCredentialPort(issuers, "V2");

        assertThat(port.issue().accessId()).isEqualTo("V2_id");
    }

    @Test
    void theFormatIsMatchedCaseInsensitively() {
        assertThat(config.issueCredentialPort(issuers, "v1").issue().accessId()).isEqualTo("V1_id");
    }

    @Test
    void aFormatNobodyCanIssueFailsFastAtStartup() {
        assertThatThrownBy(() -> config.issueCredentialPort(issuers, "V9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("V9");
    }
}
