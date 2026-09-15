package me.dhiren9939.api.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikey.service.ApiKeyExpiry;
import me.dhiren9939.api.common.ValidEnum;

@Getter
@Setter
@Schema(description = "Request to create a new API key.")
public class ApiKeyCreateDto {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Caller-assigned label for the key.", example = "CI deploy key")
    String name;

    @NotNull
    @ValidEnum(enumClass = ApiKeyExpiry.Duration.class, message = "expiry must be one of ONE_DAY, ONE_MONTH, NEVER")
    @Schema(description = "How long the key is valid for.", allowableValues = {"ONE_DAY", "ONE_MONTH", "NEVER"}, example = "ONE_MONTH")
    String expiry;
}
