package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.shared.access.events.inbound.FailureReason;

/** Optional body of a reject answer; the reason defaults to {@code REJECTED}. */
public record RejectRequestDto(FailureReason reason) {
}
