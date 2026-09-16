package me.dhiren9939.api.apikeys.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikeys.service.ApiKeyExpiry;
import me.dhiren9939.api.common.OptionalNotBlank;
import me.dhiren9939.api.common.ValidEnum;

/** All fields optional - a null field means "leave unchanged". */
@Getter
@Setter
@Schema(description = "Partial update for an API key. A null field is left unchanged.")
public class ApiKeyPatchRequest {

    @OptionalNotBlank
    @Size(max = 255)
    @Schema(description = "New label for the key. Omit to leave unchanged.", example = "CI deploy key (rotated)")
    private String name;

    @ValidEnum(enumClass = ApiKeyExpiry.Duration.class, message = "expiry must be one of ONE_DAY, ONE_MONTH, NEVER")
    @Schema(description = "New expiry, computed from now. Omit to leave unchanged.", allowableValues = {"ONE_DAY", "ONE_MONTH", "NEVER"})
    private String expiry;

}
