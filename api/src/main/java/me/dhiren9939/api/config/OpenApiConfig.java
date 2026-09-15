package me.dhiren9939.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import me.dhiren9939.api.auth.jwt.AuthCookies;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the access-token cookie as the auth scheme so "Authorize" in Swagger UI reflects how
 * the API is actually authenticated - there is no bearer header, JwtAuthenticationFilter reads
 * the access_token cookie set by the OAuth2 login / refresh flow.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "HQ API",
                description = "REST API for the HQ platform.",
                version = "v1"
        ),
        security = @SecurityRequirement(name = OpenApiConfig.ACCESS_TOKEN_COOKIE)
)
@SecurityScheme(
        name = OpenApiConfig.ACCESS_TOKEN_COOKIE,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = AuthCookies.ACCESS_TOKEN
)
public class OpenApiConfig {

    public static final String ACCESS_TOKEN_COOKIE = "accessTokenCookie";
}
