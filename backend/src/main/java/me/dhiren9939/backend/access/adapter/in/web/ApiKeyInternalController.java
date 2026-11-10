package me.dhiren9939.backend.access.adapter.in.web;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeCommand;
import me.dhiren9939.backend.access.application.port.in.ConfirmChangeUseCase;
import me.dhiren9939.backend.access.application.port.in.RejectChangeCommand;
import me.dhiren9939.backend.access.application.port.in.RejectChangeUseCase;
import me.dhiren9939.backend.shared.access.events.domain.FailureReason;
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
        confirmChange.handle(new ConfirmChangeCommand(apiKeyId, revision));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID apiKeyId, @PathVariable long revision,
                                       @RequestBody(required = false) RejectRequestDto request) {
        FailureReason reason = request == null || request.reason() == null
                ? FailureReason.REJECTED
                : request.reason();
        rejectChange.handle(new RejectChangeCommand(apiKeyId, revision, reason));
        return ResponseEntity.noContent().build();
    }
}
