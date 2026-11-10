package me.dhiren9939.backend.access.application.port.in;

public interface IssueApiKeyUseCase {
    IssuedApiKeyResult handle(IssueApiKeyCommand command);
}
