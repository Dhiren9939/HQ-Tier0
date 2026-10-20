package me.dhiren9939.api.apikey.internal;

import me.dhiren9939.api.apikey.ApiKeyPermissions;
import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.dto.CreateResponseDto;
import me.dhiren9939.api.apikey.internal.dto.CreateRequestDto;
import me.dhiren9939.api.apikey.internal.dto.GetResponseDto;
import me.dhiren9939.api.apikey.internal.dto.UpdateRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** HTTP endpoints for API-key CRUD and failed-update acknowledgement. */
@RestController
@RequestMapping("/api/v1/user/{userId}/apikey")
class ApiKeyController {

    private static final Permissions NO_PERMISSIONS = new Permissions(
            ApiKeyPermissions.Ingestion.NONE,
            ApiKeyPermissions.Tenant.NONE,
            ApiKeyPermissions.Channel.NONE
    );

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<CreateResponseDto> create(@PathVariable UUID userId,
                                                    @RequestBody CreateRequestDto request) {
        CreateResponseDto created = apiKeyService.createApiKey(
                userId, request.name(), request.expiry(),
                request.permissions() == null ? NO_PERMISSIONS : request.permissions()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{apiKeyId}")
    public GetResponseDto get(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        return apiKeyService.getApiKey(userId, apiKeyId);
    }

    @PatchMapping("/{apiKeyId}")
    public GetResponseDto update(@PathVariable UUID userId, @PathVariable UUID apiKeyId,
                                 @RequestBody UpdateRequestDto request) {
        return apiKeyService.updateApiKey(
                userId, apiKeyId, request.name(), request.expiry(),
                request.permissions() == null ? new Permissions(null, null, null) : request.permissions()
        );
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        apiKeyService.deleteApiKey(userId, apiKeyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiKeyId}/acknowledge-failure")
    public ResponseEntity<Void> acknowledgeFailure(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        apiKeyService.acknowledgeFailure(userId, apiKeyId);
        return ResponseEntity.noContent().build();
    }

}
