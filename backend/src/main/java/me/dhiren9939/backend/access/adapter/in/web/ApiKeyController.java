package me.dhiren9939.backend.access.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.ApiKeyView;
import me.dhiren9939.backend.access.application.port.in.CancelApiKeyChangeCommand;
import me.dhiren9939.backend.access.application.port.in.CancelApiKeyChangeUseCase;
import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.GetApiKeyQuery;
import me.dhiren9939.backend.access.application.port.in.GetApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.PermissionChanges;
import me.dhiren9939.backend.shared.access.AccessLevel;
import me.dhiren9939.backend.shared.access.Permissions;
import me.dhiren9939.backend.shared.access.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** HTTP endpoints for API-key CRUD, cancelling a pending change and dismissing finished or failed keys. */
@RestController
@RequestMapping("/api/v1/user/{userId}/apikey")
@RequiredArgsConstructor
class ApiKeyController {

    private final IssueApiKeyUseCase issueApiKey;
    private final GetApiKeyUseCase getApiKey;
    private final UpdateApiKeyUseCase updateApiKey;
    private final DeleteApiKeyUseCase deleteApiKey;
    private final DismissApiKeyUseCase dismissApiKey;
    private final CancelApiKeyChangeUseCase cancelApiKeyChange;

    @PostMapping
    public ResponseEntity<CreateResponseDto> create(@PathVariable UUID userId,
                                                    @Valid @RequestBody CreateRequestDto request) {
        IssuedApiKeyResult issued = issueApiKey.handle(new IssueApiKeyCommand(
                userId, request.name(), request.expiry(), Permissions.of(request.permissions())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toCreateResponse(issued));
    }

    @GetMapping("/{apiKeyId}")
    public GetResponseDto get(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        return toGetResponse(getApiKey.handle(new GetApiKeyQuery(userId, apiKeyId)));
    }

    @PatchMapping("/{apiKeyId}")
    public GetResponseDto update(@PathVariable UUID userId, @PathVariable UUID apiKeyId,
                                 @Valid @RequestBody UpdateRequestDto request) {
        return toGetResponse(updateApiKey.handle(new UpdateApiKeyCommand(
                userId, apiKeyId, request.name(), request.expiry(),
                request.permissions() == null ? null : new PermissionChanges(request.permissions()))));
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        deleteApiKey.handle(new DeleteApiKeyCommand(userId, apiKeyId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiKeyId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        cancelApiKeyChange.handle(new CancelApiKeyChangeCommand(userId, apiKeyId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiKeyId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID userId, @PathVariable UUID apiKeyId) {
        dismissApiKey.handle(new DismissApiKeyCommand(userId, apiKeyId));
        return ResponseEntity.noContent().build();
    }

    /** Every scope is listed, so clients never have to guess what a missing scope means. */
    static Map<Scope, AccessLevel> toResponseMap(Permissions permissions) {
        Map<Scope, AccessLevel> all = new EnumMap<>(Scope.class);
        for (Scope scope : Scope.values())
            all.put(scope, permissions.levelOf(scope));
        return all;
    }

    private static CreateResponseDto toCreateResponse(IssuedApiKeyResult issued) {
        ApiKeyView key = issued.key();
        return CreateResponseDto.builder()
                .apiKeyId(key.apiKeyId())
                .accessId(key.accessId())
                .accessSecret(issued.accessSecret())
                .apiKeyName(key.apiKeyName())
                .permissions(toResponseMap(key.permissions()))
                .createdAt(key.createdAt())
                .expiresAt(key.expiresAt())
                .updateStatus(key.updateStatus())
                .build();
    }

    private static GetResponseDto toGetResponse(ApiKeyView key) {
        return GetResponseDto.builder()
                .apiKeyId(key.apiKeyId())
                .accessId(key.accessId())
                .apiKeyName(key.apiKeyName())
                .permissions(toResponseMap(key.permissions()))
                .createdAt(key.createdAt())
                .expiresAt(key.expiresAt())
                .updateStatus(key.updateStatus())
                .build();
    }
}
