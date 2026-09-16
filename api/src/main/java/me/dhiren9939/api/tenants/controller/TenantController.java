package me.dhiren9939.api.tenants.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.common.ApiResponseDto;
import me.dhiren9939.api.tenants.dto.TenantCreateDto;
import me.dhiren9939.api.tenants.dto.TenantDto;
import me.dhiren9939.api.tenants.dto.TenantPageDto;
import me.dhiren9939.api.tenants.dto.TenantPatchRequest;
import me.dhiren9939.api.tenants.service.TenantService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants")
@Validated
@Tag(name = "Tenants", description = "Manage the signed-in user's tenants.")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @Operation(summary = "Create tenant", description = "Creates a new tenant for the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Tenant created.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    public ApiResponseDto<TenantDto> createTenant(@AuthenticationPrincipal AccessTokenClaims claims, @Valid @RequestBody TenantCreateDto createDto) {
        return ApiResponseDto.of(tenantService.createTenant(claims, createDto));
    }

    @PatchMapping("/{tenantId}")
    @Operation(summary = "Update tenant", description = "Partially updates a tenant owned by the signed-in user. Null fields in the request are left unchanged.")
    @ApiResponse(responseCode = "200", description = "Tenant updated.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    @ApiResponse(responseCode = "404", description = "No tenant with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<TenantDto> updateTenant(@AuthenticationPrincipal AccessTokenClaims claims,
                                                   @Parameter(description = "Id of the tenant to update.") @PathVariable UUID tenantId,
                                                   @Valid @RequestBody TenantPatchRequest patch) {
        return ApiResponseDto.of(tenantService.patchTenant(claims, tenantId, patch));
    }

    @DeleteMapping("/{tenantId}")
    @Operation(summary = "Delete tenant", description = "Permanently deletes a tenant owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Tenant deleted.")
    @ApiResponse(responseCode = "404", description = "No tenant with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<Void> deleteTenant(@AuthenticationPrincipal AccessTokenClaims claims,
                                              @Parameter(description = "Id of the tenant to delete.") @PathVariable UUID tenantId) {
        tenantService.deleteTenant(claims, tenantId);
        return ApiResponseDto.of(null);
    }

    @GetMapping
    @Operation(summary = "List tenants", description = "Lists tenants owned by the signed-in user, newest first.")
    @ApiResponse(responseCode = "200", description = "Page of tenants returned.")
    public ApiResponseDto<TenantPageDto> getTenants(@AuthenticationPrincipal AccessTokenClaims claims,
                                                     @Parameter(description = "Zero-based page number.") @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @Parameter(description = "Page size.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.of(tenantService.listTenants(claims, page, size));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant", description = "Fetches a single tenant owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Tenant returned.")
    @ApiResponse(responseCode = "404", description = "No tenant with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<TenantDto> getTenant(@AuthenticationPrincipal AccessTokenClaims claims,
                                                @Parameter(description = "Id of the tenant to fetch.") @PathVariable UUID tenantId) {
        return ApiResponseDto.of(tenantService.getTenant(claims, tenantId));
    }
}
