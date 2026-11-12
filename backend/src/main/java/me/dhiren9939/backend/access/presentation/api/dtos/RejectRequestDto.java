package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;

/** Body of a reject answer: the issuer, i.e. which downstream service rejected, and optionally why (defaults to {@code REJECTED}). */
public record RejectRequestDto(@NotBlank(message = "{ISSUER_REQUIRED}")
                                @Size(max = 256, message = "{ISSUER_TOO_LONG}")
                                @Pattern(regexp = RequestPatterns.SERVICE_IDENTIFIER, message = "{ISSUER_INVALID_FORMAT}")
                                String issuer,
                               FailureReason reason) {
}
