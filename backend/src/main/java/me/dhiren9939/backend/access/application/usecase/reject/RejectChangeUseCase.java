package me.dhiren9939.backend.access.application.usecase.reject;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeRejected;


public interface RejectChangeUseCase {
    void handle(ApiKeyChangeRejected command);
}
