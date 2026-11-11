package me.dhiren9939.backend.access.infrastructure.config;

import me.dhiren9939.backend.access.infrastructure.keyformat.CredentialIssuer;
import me.dhiren9939.backend.access.application.interfaces.IssueCredentialPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

/**
 * Wires shared infrastructure beans and selects the key format used for newly issued credentials.
 * Verification supports every registered format, so older keys keep working after the current one moves on.
 */
@Configuration
class ApiKeyConfig {

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IssueCredentialPort issueCredentialPort(List<CredentialIssuer> issuers,
                                            @Value("${access.key-format.current:V1}") String currentKeyFormat) {
        CredentialIssuer current = issuers.stream()
                .filter(issuer -> issuer.keyFormat().equalsIgnoreCase(currentKeyFormat))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No credential issuer for configured key format '" + currentKeyFormat + "'"));
        return current::issue;
    }
}
