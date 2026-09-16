package me.dhiren9939.api.tenants.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.tenants.dto.TenantCreateDto;
import me.dhiren9939.api.tenants.dto.TenantDto;
import me.dhiren9939.api.tenants.dto.TenantPageDto;
import me.dhiren9939.api.tenants.dto.TenantPatchRequest;
import me.dhiren9939.api.tenants.entity.Tenant;
import me.dhiren9939.api.tenants.repo.TenantRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepo tenantRepo;

    public TenantDto createTenant(AccessTokenClaims claims, TenantCreateDto createDto) {
        Tenant tenant = new Tenant(claims.userId(), createDto.getName().trim());
        tenant = tenantRepo.save(tenant);
        return new TenantDto(tenant);
    }

    public TenantDto patchTenant(AccessTokenClaims claims, UUID tenantId, TenantPatchRequest patch) {
        Tenant tenant = findOwned(claims, tenantId);

        if (patch.getName() != null) {
            tenant.setName(patch.getName().trim());
        }

        tenant = tenantRepo.save(tenant);

        return new TenantDto(tenant);
    }

    public TenantPageDto listTenants(AccessTokenClaims claims, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "tenantId"));
        Page<TenantDto> result = tenantRepo.findByUserId(claims.userId(), pageable).map(TenantDto::new);

        return new TenantPageDto(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public TenantDto getTenant(AccessTokenClaims claims, UUID tenantId) {
        return new TenantDto(findOwned(claims, tenantId));
    }

    public void deleteTenant(AccessTokenClaims claims, UUID tenantId) {
        tenantRepo.delete(findOwned(claims, tenantId));
    }

    private Tenant findOwned(AccessTokenClaims claims, UUID tenantId) {
        return tenantRepo.findById(tenantId)
                .filter(tenant -> tenant.getUserId().equals(claims.userId()))
                .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    }
}
