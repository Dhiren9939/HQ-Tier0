package me.dhiren9939.api.apikeys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.apikeys.dto.*;
import me.dhiren9939.api.apikeys.service.ApiKeyService;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.common.ApiResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/apikeys")
@Validated
@Tag(name = "API Keys", description = "Manage the signed-in user's API keys.")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Create API key", description = "Creates a new API key for the signed-in user. The raw secret is returned only in this response - it cannot be retrieved again.")
    @ApiResponse(responseCode = "200", description = "API key created.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    public ApiResponseDto<ApiKeyCreatedDto> createApiKey(@AuthenticationPrincipal AccessTokenClaims claims, @Valid @RequestBody ApiKeyCreateDto createDto){
        return ApiResponseDto.of(apiKeyService.createApiKey(claims,createDto));
    }

    @PatchMapping("/{apiKeyId}")
    @Operation(summary = "Update API key", description = "Partially updates an API key owned by the signed-in user. Null fields in the request are left unchanged.")
    @ApiResponse(responseCode = "200", description = "API key updated.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    @ApiResponse(responseCode = "404", description = "No API key with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ApiKeyDto> updateApiKey(@AuthenticationPrincipal AccessTokenClaims claims,
                                                @Parameter(description = "Id of the API key to update.") @PathVariable UUID apiKeyId,
                                                @Valid @RequestBody ApiKeyPatchRequest patch) {
        return ApiResponseDto.of(apiKeyService.patchApiKey(claims, apiKeyId, patch));
    }

    @DeleteMapping("/{apiKeyId}")
    @Operation(summary = "Delete API key", description = "Permanently deletes an API key owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "API key deleted.")
    @ApiResponse(responseCode = "404", description = "No API key with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<Void> deleteApiKey(@AuthenticationPrincipal AccessTokenClaims claims,
                                           @Parameter(description = "Id of the API key to delete.") @PathVariable UUID apiKeyId) {
        apiKeyService.deleteApiKey(claims, apiKeyId);
        return ApiResponseDto.of(null);
    }

    @GetMapping
    @Operation(summary = "List API keys", description = "Lists API keys owned by the signed-in user, newest first.")
    @ApiResponse(responseCode = "200", description = "Page of API keys returned.")
    public ApiResponseDto<ApiKeyPageDto> getApiKeys(@AuthenticationPrincipal AccessTokenClaims claims,
                                                  @Parameter(description = "Zero-based page number.") @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @Parameter(description = "Page size.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.of(apiKeyService.listApiKeys(claims, page, size));
    }

    @GetMapping("/{apiKeyId}")
    @Operation(summary = "Get API key", description = "Fetches a single API key owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "API key returned.")
    @ApiResponse(responseCode = "404", description = "No API key with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ApiKeyDto> getApiKey(@AuthenticationPrincipal AccessTokenClaims claims,
                                             @Parameter(description = "Id of the API key to fetch.") @PathVariable UUID apiKeyId) {
        return ApiResponseDto.of(apiKeyService.getApiKey(claims, apiKeyId));
    }
}
