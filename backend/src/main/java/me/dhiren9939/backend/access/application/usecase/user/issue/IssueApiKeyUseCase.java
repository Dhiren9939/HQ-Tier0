package me.dhiren9939.backend.access.application.usecase.user.issue;


public interface IssueApiKeyUseCase {
    IssuedApiKeyResult handle(IssueApiKeyCommand command);
}
