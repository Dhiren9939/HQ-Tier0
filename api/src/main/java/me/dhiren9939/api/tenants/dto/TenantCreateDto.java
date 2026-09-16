package me.dhiren9939.api.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to create a new tenant.")
public class TenantCreateDto {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Caller-assigned label for the tenant.", example = "Acme Merchant")
    private String name;
}
