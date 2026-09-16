package me.dhiren9939.api.channels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.channels.entity.ChannelEventType;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "An event type a channel is subscribed to.")
public class ChannelEventTypeDto {

    @Schema(description = "Id of the channel this subscription belongs to.")
    private UUID channelId;

    @Schema(description = "The subscribed event type.", example = "order.created")
    private String eventType;

    public ChannelEventTypeDto(ChannelEventType channelEventType) {
        this.channelId = channelEventType.getChannelEventTypePK().channelId();
        this.eventType = channelEventType.getChannelEventTypePK().eventType();
    }
}
