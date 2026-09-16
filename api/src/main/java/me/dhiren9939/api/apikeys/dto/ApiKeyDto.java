package me.dhiren9939.api.apikeys.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.apikeys.entity.ApiKey;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "An API key, minus its secret - the secret is only ever returned once, at creation.")
public class ApiKeyDto {

    @Schema(description = "The API key's id.")
    private UUID apiKeyId;

    @Schema(description = "Caller-assigned label for the key.", example = "CI deploy key")
    private String name;

    @Schema(description = "When the key was created.")
    private Instant createdAt;

    @Schema(description = "When the key expires. Null if it never expires.")
    private Instant expiresAt;

    public ApiKeyDto(ApiKey apiKey) {
        this.apiKeyId = apiKey.getApiKeyId();
        this.name = apiKey.getName();
        this.createdAt = apiKey.getCreatedAt();
        this.expiresAt = apiKey.getExpiresAt();
    }
}
