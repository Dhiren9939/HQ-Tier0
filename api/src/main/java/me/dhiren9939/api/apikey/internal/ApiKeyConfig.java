package me.dhiren9939.api.apikey.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Provides shared secure-random and UTC clock beans for API key services.
 */
@Configuration
class ApiKeyConfig {

    @Bean
    public SecureRandom secureRandom(){
        return new SecureRandom();
    }

    @Bean
    public Clock clock(){
        return Clock.systemUTC();
    }

}
