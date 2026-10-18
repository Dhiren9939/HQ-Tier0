package me.dhiren9939.api.apikey.internal.dto;

import lombok.Builder;

import java.util.List;

/**
 * Page of API key metadata with pagination totals.
 */
@Builder
public record GetPageResponseDto(List<GetResponseDto> items, int page, int size,
                                 long totalElements, int totalPages) {
}
