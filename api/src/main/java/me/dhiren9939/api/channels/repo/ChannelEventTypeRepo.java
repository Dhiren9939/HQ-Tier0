package me.dhiren9939.api.channels.repo;

import me.dhiren9939.api.channels.entity.ChannelEventType;
import me.dhiren9939.api.channels.entity.ChannelEventTypePK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelEventTypeRepo extends JpaRepository<ChannelEventType, ChannelEventTypePK> {
    List<ChannelEventType> findByChannelEventTypePK_ChannelId(UUID channelId);

    void deleteByChannelEventTypePK(ChannelEventTypePK channelEventTypePK);
}
