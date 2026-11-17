package me.dhiren9939.backend.access.presentation.api.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.dismiss.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.dismiss.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.get.GetApiKeyQuery;
import me.dhiren9939.backend.access.application.usecase.user.get.GetApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.PermissionChanges;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.access.presentation.api.dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** HTTP endpoints for API-key CRUD and dismissing deleted keys. */
@RestController
@RequestMapping("/api/v1/user/{userId}/apikey")
@RequiredArgsConstructor
class ApiKeyController {

    private final IssueApiKeyUseCase issueApiKey;
    private final GetApiKeyUseCase getApiKey;
    private final UpdateApiKeyUseCase updateApiKey;
    private final DeleteApiKeyUseCase deleteApiKey;
    private final DismissApiKeyUseCase dismissApiKey;

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
                .status(ApiKeyStatus.of(key.state()))
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
                .status(ApiKeyStatus.of(key.state()))
                .build();
    }
}
