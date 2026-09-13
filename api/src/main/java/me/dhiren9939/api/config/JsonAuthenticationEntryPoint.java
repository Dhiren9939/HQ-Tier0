package me.dhiren9939.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.common.ApiResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * This is what actually answers an unauthenticated request to a protected endpoint - not the
 * JWT filter (which just leaves the request unauthenticated and moves on). Without this, Spring
 * Security's oauth2Login default entry point would redirect the caller to Google's authorization
 * endpoint, which is wrong for an XHR/fetch call from the SPA. Every such call gets a flat JSON
 * 401 instead, so the frontend can decide what to do (show a login prompt, retry after refresh, etc).
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        ApiResponseWriter.writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required to access this resource.");
    }
}
