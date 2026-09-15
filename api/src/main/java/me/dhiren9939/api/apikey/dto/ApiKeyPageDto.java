package me.dhiren9939.api.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "A page of API keys belonging to the caller.")
public class ApiKeyPageDto {

    @Schema(description = "API keys on this page.")
    private List<ApiKeyDto> apiKeyList;

    @Schema(description = "Zero-based page number.", example = "0")
    private int pageNo;

    @Schema(description = "Page size.", example = "20")
    private int size;

    @Schema(description = "Total number of API keys across all pages.")
    private long totalElements;

    @Schema(description = "Total number of pages.")
    private int numberOfPages;

}
