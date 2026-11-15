package me.dhiren9939.backend.access.presentation.api.controllers;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.common.access.ApiKeyVerificationFacade;
import me.dhiren9939.backend.common.access.ApiKeyVerifyResult;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only endpoint. Nothing in the application exposes verification over HTTP yet, so this lets the
 * integration tests prove that a downstream service translating a failed result reaches the exception handler
 * (which only advises controllers in this package) as 401 or 400 rather than 500.
 */
@RestController
@RequiredArgsConstructor
class VerifyProbeController {
    private final ApiKeyVerificationFacade facade;

    @GetMapping("/test-probe/verify")
    Map<String, Object> verify(@RequestParam String accessId, @RequestParam String secret) {
        return switch (facade.verifyApiKey(new VerifyApiKeyQuery(accessId, secret))) {
            case ApiKeyVerifyResult.Success success -> Map.of(
                    "apiKeyId", success.apiKeyView().apiKeyId().toString(),
                    "userId", success.apiKeyView().userId().toString());
            case ApiKeyVerifyResult.Failure failure ->
                    throw new ApiKeyVerificationFailure(failure.reason(), failure.message());
        };
    }
}
