package me.dhiren9939.api.apikey.internal.dto;

import me.dhiren9939.api.apikey.Permissions;
import me.dhiren9939.api.apikey.internal.ApiKeyExpiry;

public record UpdateRequestDto(String name, ApiKeyExpiry.Duration expiry, Permissions permissions) {
}
