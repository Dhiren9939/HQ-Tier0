package me.dhiren9939.backend.access.presentation.api.controllers;

import jakarta.validation.Valid;
import me.dhiren9939.backend.access.presentation.api.dtos.ConfirmRequestDto;
import me.dhiren9939.backend.access.presentation.api.dtos.RejectRequestDto;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;
import me.dhiren9939.backend.access.application.usecase.service.confirm.ConfirmChangeUseCase;
import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;
import me.dhiren9939.backend.access.application.usecase.service.reject.RejectChangeUseCase;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Test harness for the data plane's answer to a pending change. Confirm and reject are service-to-service
 * messages, never user actions; the use cases ({@code ConfirmChangeUseCase}, {@code RejectChangeUseCase}) are the
 * real interface and a message consumer will call them later. These HTTP endpoints exist so the answers can be
 * sent from Postman, and should be removed or locked down to the network/gateway level once that consumer exists.
 * The caller names the revision it is answering for and identifies itself with an {@code issuer}.
 */
@RestController
@RequestMapping("/internal/apikey/{apiKeyId}/changes/{revision}")
@RequiredArgsConstructor
class ApiKeyInternalController {

    private final ConfirmChangeUseCase confirmChange;
    private final RejectChangeUseCase rejectChange;

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID apiKeyId, @PathVariable long revision,
                                        @Valid @RequestBody ConfirmRequestDto request) {
        confirmChange.handle(new ApiKeyChangeConfirmed(apiKeyId, revision, request.issuer()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID apiKeyId, @PathVariable long revision,
                                       @Valid @RequestBody RejectRequestDto request) {
        FailureReason reason = request.reason() == null
                ? FailureReason.REJECTED
                : request.reason();
        rejectChange.handle(new ApiKeyChangeRejected(apiKeyId, revision, request.issuer(), reason));
        return ResponseEntity.noContent().build();
    }
}
