package me.dhiren9939.api.apikeys.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikeys.dto.*;
import me.dhiren9939.api.apikeys.entity.ApiKey;
import me.dhiren9939.api.apikeys.repo.ApiKeyRepo;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepo apiKeyRepo;
    private final ApiKeyExpiry apiKeyExpiry;
    private final ApiKeySecretGenerator apiKeySecretGenerator;

    public ApiKeyCreatedDto createApiKey(AccessTokenClaims claims, ApiKeyCreateDto createDto) {
        String apikeyString = apiKeySecretGenerator.generate();
        String keyHash = apiKeySecretGenerator.hash(apikeyString);
        Instant now = Instant.now();
        Instant expiry = apiKeyExpiry.getExpiry(now, createDto.getExpiry());

        ApiKey apiKey = new ApiKey(claims.userId(), createDto.getName().trim(), keyHash, now, expiry);
        apiKey = apiKeyRepo.save(apiKey);

        return new ApiKeyCreatedDto(apikeyString, apiKey);
    }

    public ApiKeyDto patchApiKey(AccessTokenClaims claims, UUID apiKeyId, ApiKeyPatchRequest patch) {
        ApiKey apiKey = findOwned(claims, apiKeyId);

        if (patch.getName() != null) {
            apiKey.setName(patch.getName().trim());
        }
        if (patch.getExpiry() != null) {
            apiKey.setExpiresAt(apiKeyExpiry.getExpiry(Instant.now(), patch.getExpiry()));
        }

        apiKey = apiKeyRepo.save(apiKey);

        return new ApiKeyDto(apiKey);
    }

    public ApiKeyPageDto listApiKeys(AccessTokenClaims claims, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ApiKeyDto> result = apiKeyRepo.findByUserId(claims.userId(), pageable).map(ApiKeyDto::new);

        return new ApiKeyPageDto(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public ApiKeyDto getApiKey(AccessTokenClaims claims, UUID apiKeyId) {
        return new ApiKeyDto(findOwned(claims, apiKeyId));
    }

    public void deleteApiKey(AccessTokenClaims claims, UUID apiKeyId) {
        apiKeyRepo.delete(findOwned(claims, apiKeyId));
    }

    private ApiKey findOwned(AccessTokenClaims claims, UUID apiKeyId) {
        return apiKeyRepo.findById(apiKeyId)
                .filter(key -> key.getUserId().equals(claims.userId()))
                .orElseThrow(() -> new NoSuchElementException("API key not found"));
    }
}
