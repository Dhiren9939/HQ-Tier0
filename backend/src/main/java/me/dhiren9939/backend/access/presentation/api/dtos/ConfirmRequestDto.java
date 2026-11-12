package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of a confirm answer: the issuer, i.e. which downstream service applied the change. */
public record ConfirmRequestDto(@NotBlank(message = "{ISSUER_REQUIRED}")
                                @Size(max = 256, message = "{ISSUER_TOO_LONG}")
                                @Pattern(regexp = RequestPatterns.SERVICE_IDENTIFIER, message = "{ISSUER_INVALID_FORMAT}")
                                String issuer) {
}
