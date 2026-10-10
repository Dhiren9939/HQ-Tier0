package me.dhiren9939.api.apikey.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
class ApiKeyConfig {

    @Bean
    public SecureRandom secureRandom(){
        return new SecureRandom();
    }

}
