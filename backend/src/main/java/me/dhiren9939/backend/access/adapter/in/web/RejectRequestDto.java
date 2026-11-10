package me.dhiren9939.backend.access.adapter.in.web;

import me.dhiren9939.backend.access.domain.FailureReason;

/** Optional body of a reject answer; the reason defaults to {@code REJECTED}. */
public record RejectRequestDto(FailureReason reason) {
}
