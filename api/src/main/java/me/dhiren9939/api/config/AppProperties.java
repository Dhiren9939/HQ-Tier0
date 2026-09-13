package me.dhiren9939.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    /** Where to send the browser after a successful login. Never taken from a request param - open-redirect risk. */
    private String frontendPostLoginUrl;

    /** Where to send the browser after a failed login (an "?error=" code is appended). */
    private String frontendLoginUrl;
}
