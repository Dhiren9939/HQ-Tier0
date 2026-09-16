package me.dhiren9939.api.channels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to subscribe a channel to an event type.")
public class ChannelEventTypeCreateDto {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "The event type to subscribe to.", example = "order.created")
    private String eventType;
}
