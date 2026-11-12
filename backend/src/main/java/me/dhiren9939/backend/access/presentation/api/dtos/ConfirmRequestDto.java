package me.dhiren9939.backend.access.presentation.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of a confirm answer: the issuer, i.e. which downstream service applied the change. */
public record ConfirmRequestDto(@NotBlank @Size(max = 256)
                                @Pattern(regexp = RequestPatterns.SERVICE_IDENTIFIER,
                                        message = "must start with a letter or digit and contain only letters, digits and . _ : / -")
                                String issuer) {
}
