package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.presentation.api.dtos.RejectRequestDto;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;
import me.dhiren9939.backend.access.application.usecase.confirm.ConfirmChangeUseCase;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;
import me.dhiren9939.backend.access.application.usecase.reject.RejectChangeUseCase;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal HTTP endpoints for the data plane's answer to a pending change. The caller names the
 * revision it is answering for. These are not user-facing and should be secured at the
 * network/gateway level.
 */
@RestController
@RequestMapping("/internal/apikey/{apiKeyId}/changes/{revision}")
@RequiredArgsConstructor
class ApiKeyInternalController {

    private final ConfirmChangeUseCase confirmChange;
    private final RejectChangeUseCase rejectChange;

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID apiKeyId, @PathVariable long revision) {
        confirmChange.handle(new ApiKeyChangeConfirmed(apiKeyId, revision));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID apiKeyId, @PathVariable long revision,
                                       @RequestBody(required = false) RejectRequestDto request) {
        FailureReason reason = request == null || request.reason() == null
                ? FailureReason.REJECTED
                : request.reason();
        rejectChange.handle(new ApiKeyChangeRejected(apiKeyId, revision, reason));
        return ResponseEntity.noContent().build();
    }
}
