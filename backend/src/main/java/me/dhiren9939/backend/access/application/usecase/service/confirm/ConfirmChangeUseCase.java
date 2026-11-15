package me.dhiren9939.backend.access.application.usecase.service.confirm;

import me.dhiren9939.backend.common.access.events.inbound.ApiKeyChangeConfirmed;


public interface ConfirmChangeUseCase {
    void handle(ApiKeyChangeConfirmed command);
}
