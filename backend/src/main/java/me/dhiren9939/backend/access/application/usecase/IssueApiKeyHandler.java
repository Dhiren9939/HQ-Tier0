package me.dhiren9939.backend.access.application.usecase;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.port.in.ApiKeyView;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.port.in.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.port.in.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.port.out.ApiKeys;
import me.dhiren9939.backend.access.application.port.out.IssueCredentialPort;
import me.dhiren9939.backend.access.domain.ApiKey;
import me.dhiren9939.backend.access.domain.IssuedCredential;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
class IssueApiKeyHandler implements IssueApiKeyUseCase {
    private final ApiKeys apiKeys;
    private final IssueCredentialPort credentials;
    private final Clock clock;

    @Override
    @Transactional
    public IssuedApiKeyResult handle(IssueApiKeyCommand command) {
        IssuedCredential credential = credentials.issue();
        Instant createdAt = clock.instant();

        ApiKey key = ApiKey.issue(command.userId(), command.name(), credential, command.permissions(),
                createdAt, command.lifetime().expiryFrom(createdAt));
        apiKeys.save(key);

        return new IssuedApiKeyResult(ApiKeyView.of(key), credential.accessSecret());
    }
}
