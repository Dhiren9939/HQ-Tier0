package me.dhiren9939.api.channels.repo;

import me.dhiren9939.api.channels.entity.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChannelRepo extends JpaRepository<Channel, UUID> {
    Page<Channel> findByTenantId(UUID tenantId, Pageable pageable);
}
