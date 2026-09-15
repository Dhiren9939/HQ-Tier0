package me.dhiren9939.api.apikey.repo;

import me.dhiren9939.api.apikey.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiKeyRepo extends JpaRepository<ApiKey, UUID> {
    Page<ApiKey> findByUserId(UUID userId, Pageable pageable);
}
