package me.dhiren9939.api.channels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.channels.entity.Channel;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "A channel belonging to a tenant.")
public class ChannelDto {

    @Schema(description = "The channel's id.")
    private UUID channelId;

    @Schema(description = "Id of the tenant this channel belongs to.")
    private UUID tenantId;

    @Schema(description = "Webhook callback URL events are delivered to.", example = "https://example.com/webhooks/hq")
    private String callBackUrl;

    public ChannelDto(Channel channel) {
        this.channelId = channel.getChannelId();
        this.tenantId = channel.getTenantId();
        this.callBackUrl = channel.getCallBackUrl();
    }
}
