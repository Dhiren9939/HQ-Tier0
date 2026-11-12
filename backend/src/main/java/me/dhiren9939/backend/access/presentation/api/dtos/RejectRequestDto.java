package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;

/** Body of a reject answer: the issuer, i.e. which downstream service rejected, and optionally why (defaults to {@code REJECTED}). */
public record RejectRequestDto(@NotBlank @Size(max = 256)
                               @Pattern(regexp = RequestPatterns.SERVICE_IDENTIFIER,
                                       message = "must start with a letter or digit and contain only letters, digits and . _ : / -")
                               String issuer,
                               FailureReason reason) {
}
