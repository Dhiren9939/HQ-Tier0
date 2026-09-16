package me.dhiren9939.api.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.tenants.entity.Tenant;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "A tenant owned by the signed-in user.")
public class TenantDto {

    @Schema(description = "The tenant's id.")
    private UUID tenantId;

    @Schema(description = "Caller-assigned label for the tenant.", example = "Acme Merchant")
    private String name;

    public TenantDto(Tenant tenant) {
        this.tenantId = tenant.getTenantId();
        this.name = tenant.getName();
    }
}
