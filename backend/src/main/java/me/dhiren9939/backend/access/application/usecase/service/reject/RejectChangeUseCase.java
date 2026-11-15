package me.dhiren9939.backend.access.application.usecase.service.reject;

import me.dhiren9939.backend.common.access.events.inbound.ApiKeyChangeRejected;


public interface RejectChangeUseCase {
    void handle(ApiKeyChangeRejected command);
}
