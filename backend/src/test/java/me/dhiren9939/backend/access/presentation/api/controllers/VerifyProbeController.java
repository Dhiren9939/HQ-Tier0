package me.dhiren9939.backend.access.presentation.api.controllers;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.shared.access.ApiKeyFacade;
import me.dhiren9939.backend.shared.access.ApiKeyVerifyDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only endpoint. Nothing in the application exposes verification over HTTP yet, so this lets the
 * integration tests prove that verification and parsing failures reach the exception handler (which only
 * advises controllers in this package) as 401 and 400 rather than 500.
 */
@RestController
@RequiredArgsConstructor
class VerifyProbeController {
    private final ApiKeyFacade facade;

    @GetMapping("/test-probe/verify")
    Map<String, Object> verify(@RequestParam String accessId, @RequestParam String secret) {
        ApiKeyVerifyDto verified = facade.verifyApiKey(accessId, secret);
        return Map.of("apiKeyId", verified.apiKeyId().toString(), "userId", verified.userId().toString());
    }
}
