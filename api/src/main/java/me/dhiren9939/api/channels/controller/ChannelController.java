package me.dhiren9939.api.channels.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.channels.dto.ChannelCreateDto;
import me.dhiren9939.api.channels.dto.ChannelDto;
import me.dhiren9939.api.channels.dto.ChannelEventTypeCreateDto;
import me.dhiren9939.api.channels.dto.ChannelEventTypeDto;
import me.dhiren9939.api.channels.dto.ChannelPageDto;
import me.dhiren9939.api.channels.dto.ChannelPatchRequest;
import me.dhiren9939.api.channels.service.ChannelService;
import me.dhiren9939.api.common.ApiResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants/{tenantId}/channels")
@Validated
@Tag(name = "Channels", description = "Manage webhook channels belonging to the signed-in user's tenants.")
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    @Operation(summary = "Create channel", description = "Creates a new channel for a tenant owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Channel created.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    @ApiResponse(responseCode = "404", description = "No tenant with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ChannelDto> createChannel(@AuthenticationPrincipal AccessTokenClaims claims,
                                                     @Parameter(description = "Id of the tenant to create the channel under.") @PathVariable UUID tenantId,
                                                     @Valid @RequestBody ChannelCreateDto createDto) {
        return ApiResponseDto.of(channelService.createChannel(claims, tenantId, createDto));
    }

    @PatchMapping("/{channelId}")
    @Operation(summary = "Update channel", description = "Partially updates a channel owned by the signed-in user. Null fields in the request are left unchanged.")
    @ApiResponse(responseCode = "200", description = "Channel updated.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ChannelDto> updateChannel(@AuthenticationPrincipal AccessTokenClaims claims,
                                                     @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                                     @Parameter(description = "Id of the channel to update.") @PathVariable UUID channelId,
                                                     @Valid @RequestBody ChannelPatchRequest patch) {
        return ApiResponseDto.of(channelService.patchChannel(claims, tenantId, channelId, patch));
    }

    @DeleteMapping("/{channelId}")
    @Operation(summary = "Delete channel", description = "Permanently deletes a channel owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Channel deleted.")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<Void> deleteChannel(@AuthenticationPrincipal AccessTokenClaims claims,
                                               @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                               @Parameter(description = "Id of the channel to delete.") @PathVariable UUID channelId) {
        channelService.deleteChannel(claims, tenantId, channelId);
        return ApiResponseDto.of(null);
    }

    @GetMapping
    @Operation(summary = "List channels", description = "Lists channels belonging to a tenant owned by the signed-in user, newest first.")
    @ApiResponse(responseCode = "200", description = "Page of channels returned.")
    @ApiResponse(responseCode = "404", description = "No tenant with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ChannelPageDto> getChannels(@AuthenticationPrincipal AccessTokenClaims claims,
                                                       @Parameter(description = "Id of the tenant to list channels for.") @PathVariable UUID tenantId,
                                                       @Parameter(description = "Zero-based page number.") @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @Parameter(description = "Page size.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponseDto.of(channelService.listChannels(claims, tenantId, page, size));
    }

    @GetMapping("/{channelId}")
    @Operation(summary = "Get channel", description = "Fetches a single channel owned by the signed-in user.")
    @ApiResponse(responseCode = "200", description = "Channel returned.")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ChannelDto> getChannel(@AuthenticationPrincipal AccessTokenClaims claims,
                                                  @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                                  @Parameter(description = "Id of the channel to fetch.") @PathVariable UUID channelId) {
        return ApiResponseDto.of(channelService.getChannel(claims, tenantId, channelId));
    }

    @GetMapping("/{channelId}/event-types")
    @Operation(summary = "List channel event types", description = "Lists the event types a channel is subscribed to.")
    @ApiResponse(responseCode = "200", description = "Event types returned.")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<List<ChannelEventTypeDto>> getEventTypes(@AuthenticationPrincipal AccessTokenClaims claims,
                                                                     @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                                                     @Parameter(description = "Id of the channel to list event types for.") @PathVariable UUID channelId) {
        return ApiResponseDto.of(channelService.listEventTypes(claims, tenantId, channelId));
    }

    @PostMapping("/{channelId}/event-types")
    @Operation(summary = "Subscribe channel to event type", description = "Subscribes a channel to an event type.")
    @ApiResponse(responseCode = "200", description = "Event type added.")
    @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED).")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<ChannelEventTypeDto> addEventType(@AuthenticationPrincipal AccessTokenClaims claims,
                                                              @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                                              @Parameter(description = "Id of the channel to subscribe.") @PathVariable UUID channelId,
                                                              @Valid @RequestBody ChannelEventTypeCreateDto createDto) {
        return ApiResponseDto.of(channelService.addEventType(claims, tenantId, channelId, createDto));
    }

    @DeleteMapping("/{channelId}/event-types/{eventType}")
    @Operation(summary = "Unsubscribe channel from event type", description = "Removes an event type subscription from a channel.")
    @ApiResponse(responseCode = "200", description = "Event type removed.")
    @ApiResponse(responseCode = "404", description = "No tenant or channel with this id owned by the caller (NOT_FOUND).")
    public ApiResponseDto<Void> removeEventType(@AuthenticationPrincipal AccessTokenClaims claims,
                                                 @Parameter(description = "Id of the tenant the channel belongs to.") @PathVariable UUID tenantId,
                                                 @Parameter(description = "Id of the channel to unsubscribe.") @PathVariable UUID channelId,
                                                 @Parameter(description = "Event type to remove.") @PathVariable String eventType) {
        channelService.removeEventType(claims, tenantId, channelId, eventType);
        return ApiResponseDto.of(null);
    }
}
