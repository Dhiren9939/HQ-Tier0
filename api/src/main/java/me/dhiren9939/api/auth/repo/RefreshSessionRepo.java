package me.dhiren9939.api.auth.repo;

import me.dhiren9939.api.auth.entity.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface RefreshSessionRepo extends JpaRepository<RefreshSession, UUID> {

    @Modifying
    @Query("update RefreshSession r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    void revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Deletes rows with nothing left to do: expired (whether ever used or not - an expired
     * token is rejected by rotate() regardless of whether the row still exists), or revoked
     * long enough ago that the retention window for incident review has passed.
     */
    @Modifying
    @Query("delete from RefreshSession r where r.expiresAt < :now or r.revokedAt < :revokedBefore")
    int purgeDead(@Param("now") Instant now, @Param("revokedBefore") Instant revokedBefore);
}
