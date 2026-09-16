package me.dhiren9939.api.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "A page of tenants belonging to the caller.")
public class TenantPageDto {

    @Schema(description = "Tenants on this page.")
    private List<TenantDto> tenantList;

    @Schema(description = "Zero-based page number.", example = "0")
    private int pageNo;

    @Schema(description = "Page size.", example = "20")
    private int size;

    @Schema(description = "Total number of tenants across all pages.")
    private long totalElements;

    @Schema(description = "Total number of pages.")
    private int numberOfPages;
}
