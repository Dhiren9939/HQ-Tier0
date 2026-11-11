package me.dhiren9939.backend.access.application.usecase.confirm;

import me.dhiren9939.backend.shared.access.events.inbound.ApiKeyChangeConfirmed;


public interface ConfirmChangeUseCase {
    void handle(ApiKeyChangeConfirmed command);
}
