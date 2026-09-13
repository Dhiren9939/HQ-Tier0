package me.dhiren9939.api.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded HMAC-SHA256 signing key (>= 256 bits). Per-environment, never committed as a real secret. */
    private String secret;

    private long accessTokenTtlMinutes = 15;
}
