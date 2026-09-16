package me.dhiren9939.api.channels.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.channels.dto.ChannelCreateDto;
import me.dhiren9939.api.channels.dto.ChannelDto;
import me.dhiren9939.api.channels.dto.ChannelEventTypeCreateDto;
import me.dhiren9939.api.channels.dto.ChannelEventTypeDto;
import me.dhiren9939.api.channels.dto.ChannelPageDto;
import me.dhiren9939.api.channels.dto.ChannelPatchRequest;
import me.dhiren9939.api.channels.entity.Channel;
import me.dhiren9939.api.channels.entity.ChannelEventType;
import me.dhiren9939.api.channels.entity.ChannelEventTypePK;
import me.dhiren9939.api.channels.repo.ChannelEventTypeRepo;
import me.dhiren9939.api.channels.repo.ChannelRepo;
import me.dhiren9939.api.tenants.entity.Tenant;
import me.dhiren9939.api.tenants.repo.TenantRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepo channelRepo;
    private final ChannelEventTypeRepo channelEventTypeRepo;
    private final TenantRepo tenantRepo;

    public ChannelDto createChannel(AccessTokenClaims claims, UUID tenantId, ChannelCreateDto createDto) {
        findOwnedTenant(claims, tenantId);

        Channel channel = new Channel(tenantId, createDto.getCallBackUrl().trim());
        channel = channelRepo.save(channel);
        return new ChannelDto(channel);
    }

    public ChannelDto patchChannel(AccessTokenClaims claims, UUID tenantId, UUID channelId, ChannelPatchRequest patch) {
        Channel channel = findOwnedChannel(claims, tenantId, channelId);

        if (patch.getCallBackUrl() != null) {
            channel.setCallBackUrl(patch.getCallBackUrl().trim());
        }

        channel = channelRepo.save(channel);

        return new ChannelDto(channel);
    }

    public ChannelPageDto listChannels(AccessTokenClaims claims, UUID tenantId, int page, int size) {
        findOwnedTenant(claims, tenantId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "channelId"));
        Page<ChannelDto> result = channelRepo.findByTenantId(tenantId, pageable).map(ChannelDto::new);

        return new ChannelPageDto(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public ChannelDto getChannel(AccessTokenClaims claims, UUID tenantId, UUID channelId) {
        return new ChannelDto(findOwnedChannel(claims, tenantId, channelId));
    }

    public void deleteChannel(AccessTokenClaims claims, UUID tenantId, UUID channelId) {
        channelRepo.delete(findOwnedChannel(claims, tenantId, channelId));
    }

    public List<ChannelEventTypeDto> listEventTypes(AccessTokenClaims claims, UUID tenantId, UUID channelId) {
        findOwnedChannel(claims, tenantId, channelId);

        return channelEventTypeRepo.findByChannelEventTypePK_ChannelId(channelId).stream()
                .map(ChannelEventTypeDto::new)
                .toList();
    }

    public ChannelEventTypeDto addEventType(AccessTokenClaims claims, UUID tenantId, UUID channelId, ChannelEventTypeCreateDto createDto) {
        findOwnedChannel(claims, tenantId, channelId);

        ChannelEventTypePK pk = new ChannelEventTypePK(channelId, createDto.getEventType().trim());
        ChannelEventType channelEventType = channelEventTypeRepo.save(new ChannelEventType(pk, tenantId));
        return new ChannelEventTypeDto(channelEventType);
    }

    @Transactional
    public void removeEventType(AccessTokenClaims claims, UUID tenantId, UUID channelId, String eventType) {
        findOwnedChannel(claims, tenantId, channelId);

        channelEventTypeRepo.deleteByChannelEventTypePK(new ChannelEventTypePK(channelId, eventType));
    }

    private Tenant findOwnedTenant(AccessTokenClaims claims, UUID tenantId) {
        return tenantRepo.findById(tenantId)
                .filter(tenant -> tenant.getUserId().equals(claims.userId()))
                .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    }

    private Channel findOwnedChannel(AccessTokenClaims claims, UUID tenantId, UUID channelId) {
        findOwnedTenant(claims, tenantId);

        return channelRepo.findById(channelId)
                .filter(channel -> channel.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NoSuchElementException("Channel not found"));
    }
}
