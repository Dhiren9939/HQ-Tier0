package me.dhiren9939.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "HQ API",
                description = "REST API for the HQ platform.",
                version = "v1"
        )
)
public class OpenApiConfig {
}
