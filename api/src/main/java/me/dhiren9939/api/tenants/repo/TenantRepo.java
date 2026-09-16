package me.dhiren9939.api.tenants.repo;

import me.dhiren9939.api.tenants.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantRepo extends JpaRepository<Tenant, UUID> {
    Page<Tenant> findByUserId(UUID userId, Pageable pageable);
}
