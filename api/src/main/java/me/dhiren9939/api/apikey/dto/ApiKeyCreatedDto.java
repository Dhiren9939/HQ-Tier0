package me.dhiren9939.api.apikey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikey.entity.ApiKey;

/** Response for key creation only - the one time the raw secret is ever returned. */
@Getter
@Setter
@Schema(description = "An API key including its raw secret. Returned only once, at creation - it is never retrievable again.")
public class ApiKeyCreatedDto extends ApiKeyDto {

    @Schema(description = "The raw API key secret. Store it now - it cannot be retrieved again.", example = "hq-01919b1e-7c2a-7c3e-8f1a-2b3c4d5e6f70")
    private String rawKey;

    public ApiKeyCreatedDto(String rawKey, ApiKey apiKey) {
        super(apiKey);
        this.rawKey = rawKey;
    }
}
