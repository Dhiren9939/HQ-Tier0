package me.dhiren9939.api.channels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import me.dhiren9939.api.common.OptionalNotBlank;

/** All fields optional - a null field means "leave unchanged". */
@Getter
@Setter
@Schema(description = "Partial update for a channel. A null field is left unchanged.")
public class ChannelPatchRequest {

    @OptionalNotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+", message = "must be a valid http(s) URL")
    @Schema(description = "New webhook callback URL. Omit to leave unchanged.", example = "https://example.com/webhooks/hq-v2")
    private String callBackUrl;
}
