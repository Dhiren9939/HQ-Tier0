package me.dhiren9939.api.auth.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.refresh-token")
@Getter
@Setter
public class RefreshTokenProperties {

    private long ttlDays = 30;

    /** How long a revoked/replaced session row is kept after revocation, for audit/incident review, before purge. */
    private long revokedRetentionDays = 30;
}
