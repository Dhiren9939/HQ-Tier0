package me.dhiren9939.api.channels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to create a new channel.")
public class ChannelCreateDto {

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+", message = "must be a valid http(s) URL")
    @Schema(description = "Webhook callback URL events are delivered to.", example = "https://example.com/webhooks/hq")
    private String callBackUrl;
}
