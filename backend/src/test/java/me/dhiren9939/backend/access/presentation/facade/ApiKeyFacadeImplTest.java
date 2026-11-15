package me.dhiren9939.backend.access.presentation.facade;

import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifiedApiKey;
import me.dhiren9939.backend.access.application.usecase.service.verify.VerifyApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.AccessLevel;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Scope;
import me.dhiren9939.backend.common.access.ApiKeyVerificationFacade;
import me.dhiren9939.backend.common.access.ApiKeyVerifyResult;
import me.dhiren9939.backend.common.access.VerificationFailureCode;
import me.dhiren9939.backend.common.access.VerifyApiKeyQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The facade translates the verified key, or a rejection, into the common contract other modules consume. */
class ApiKeyFacadeImplTest {

    private static final UUID KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-15T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-02-15T12:00:00Z");
    private static final VerifyApiKeyQuery QUERY = new VerifyApiKeyQuery("V1_acc", "secret_raw");

    private VerifyApiKeyUseCase useCase;
    private ApiKeyVerificationFacade facade;

    @BeforeEach
    void setUp() {
        useCase = mock(VerifyApiKeyUseCase.class);
        facade = new ApiKeyFacadeImpl(useCase);
    }

    @Test
    void mapsTheVerifiedKeyToASuccessfulResult() {
        when(useCase.handle(QUERY)).thenReturn(new VerifiedApiKey(KEY_ID, USER_ID, "test-key",
                Permissions.none().with(Scope.TENANT, AccessLevel.READ), CREATED_AT, EXPIRES_AT));

        ApiKeyVerifyResult result = facade.verifyApiKey(QUERY);

        assertThat(result).isInstanceOfSatisfying(ApiKeyVerifyResult.Success.class, success -> {
            assertThat(success.apiKeyView().apiKeyId()).isEqualTo(KEY_ID);
            assertThat(success.apiKeyView().userId()).isEqualTo(USER_ID);
            assertThat(success.apiKeyView().apiKeyName()).isEqualTo("test-key");
            assertThat(success.apiKeyView().permissions()).isEqualTo(Map.of("TENANT", "READ"));
            assertThat(success.apiKeyView().createdAt()).isEqualTo(CREATED_AT);
            assertThat(success.apiKeyView().expiresAt()).isEqualTo(EXPIRES_AT);
        });
        verify(useCase).handle(QUERY);
    }

    @Test
    void noPermissionsBecomeAnEmptyMap() {
        when(useCase.handle(QUERY)).thenReturn(new VerifiedApiKey(KEY_ID, USER_ID, "test-key",
                Permissions.none(), CREATED_AT, null));

        ApiKeyVerifyResult.Success success = (ApiKeyVerifyResult.Success) facade.verifyApiKey(QUERY);
        assertThat(success.apiKeyView().permissions()).isEmpty();
        assertThat(success.apiKeyView().expiresAt()).isNull();
    }

    @Test
    void mapsARejectionToAFailedResultInsteadOfThrowing() {
        when(useCase.handle(QUERY)).thenThrow(ApiKeyVerificationFailure.expired());

        ApiKeyVerifyResult result = facade.verifyApiKey(QUERY);

        assertThat(result).isInstanceOfSatisfying(ApiKeyVerifyResult.Failure.class, failure -> {
            assertThat(failure.reason()).isEqualTo(VerificationFailureCode.KEY_EXPIRED);
            assertThat(failure.message()).isEqualTo("The API key has expired.");
        });
    }
}
