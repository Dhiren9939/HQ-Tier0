package me.dhiren9939.api.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.common.OptionalNotBlank;

/** All fields optional - a null field means "leave unchanged". */
@Getter
@Setter
@Schema(description = "Partial update for a tenant. A null field is left unchanged.")
public class TenantPatchRequest {

    @OptionalNotBlank
    @Size(max = 255)
    @Schema(description = "New label for the tenant. Omit to leave unchanged.", example = "Acme Merchant (renamed)")
    private String name;
}
