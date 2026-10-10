package me.dhiren9939.api.apikey.internal.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record GetPageResponseDto(List<GetResponseDto> items, int page, int size,
                                 long totalElements, int totalPages) {
}
