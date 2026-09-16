package me.dhiren9939.api.channels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "channel_event_types")
public class ChannelEventType {
    @EmbeddedId
    private ChannelEventTypePK channelEventTypePK;

    @Column(nullable = false)
    private UUID tenantId;

    public ChannelEventType(ChannelEventTypePK channelEventTypePK, UUID tenantId) {
        this.channelEventTypePK = channelEventTypePK;
        this.tenantId = tenantId;
    }
}
