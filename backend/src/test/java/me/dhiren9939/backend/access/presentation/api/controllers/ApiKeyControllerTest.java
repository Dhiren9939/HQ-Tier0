package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.user.delete.DeleteApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.dismiss.DismissApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.dismiss.DismissApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.get.GetApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssueApiKeyUseCase;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyCommand;
import me.dhiren9939.backend.access.application.usecase.user.update.UpdateApiKeyUseCase;
import me.dhiren9939.backend.access.domain.aggregates.apikey.*;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.exceptions.UpdateFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: checks routing, JSON shapes, command mapping and the exception-to-status advice. */
class ApiKeyControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Permissions NO_PERMISSIONS = Permissions.none();
    private static final ApiKeyView VIEW = new ApiKeyView(KEY_ID, USER_ID, "V1_acc", "test-key", NO_PERMISSIONS,
            Instant.parse("2026-01-15T12:00:00Z"), null, ApiKeyState.ACTIVE);

    private final List<Object> received = new ArrayList<>();
    private RuntimeException failure;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        IssueApiKeyUseCase issue = command -> {
            received.add(command);
            return new IssuedApiKeyResult(VIEW, "the-secret");
        };
        GetApiKeyUseCase get = query -> {
            if (failure != null) throw failure;
            return VIEW;
        };
        UpdateApiKeyUseCase update = command -> {
            received.add(command);
            if (failure != null) throw failure;
            return VIEW;
        };
        DeleteApiKeyUseCase delete = command -> received.add(command);
        DismissApiKeyUseCase dismiss = command -> received.add(command);

        mvc = MockMvcBuilders
                .standaloneSetup(new ApiKeyController(issue, get, update, delete, dismiss))
                .setControllerAdvice(new ApiKeyExceptionHandler())
                .build();
    }

    @Test
    void creatingAKeyReturns201WithTheSecretAndStateAndFillsMissingPermissionsWithNone() throws Exception {
        mvc.perform(post("/api/v1/user/{userId}/apikey", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test-key\",\"expiry\":\"NEVER\",\"permissions\":{\"INGESTION\":\"WRITE\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessSecret").value("the-secret"))
                .andExpect(jsonPath("$.accessId").value("V1_acc"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revision").doesNotExist());

        IssueApiKeyCommand command = (IssueApiKeyCommand) received.getFirst();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.lifetime()).isEqualTo(Lifetime.NEVER);
        assertThat(command.permissions()).isEqualTo(Permissions.none().with(Scope.INGESTION, AccessLevel.WRITE));
    }

    @Test
    void responsesListEveryScopeWithNoneForTheOnesNeverGranted() throws Exception {
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.INGESTION").value("NONE"))
                .andExpect(jsonPath("$.permissions.TENANT").value("NONE"))
                .andExpect(jsonPath("$.permissions.CHANNEL").value("NONE"));
    }

    @Test
    void aLevelTheScopeDoesNotAcceptIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/user/{userId}/apikey", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"INGESTION\":\"READ\"}}"))
                .andExpect(status().isBadRequest());

        assertThat(received).isEmpty();
    }

    @Test
    void anUnknownScopeOrLevelNameIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/user/{userId}/apikey", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"BILLING\":\"READ\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/user/{userId}/apikey", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"TENANT\":\"ADMIN\"}}"))
                .andExpect(status().isBadRequest());

        assertThat(received).isEmpty();
    }

    @Test
    void aLevelTheScopeDoesNotAcceptOnPatchIsABadRequest() throws Exception {
        mvc.perform(patch("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":{\"INGESTION\":\"READ_WRITE\"}}"))
                .andExpect(status().isBadRequest());

        assertThat(received).isEmpty();
    }

    @Test
    void patchSendsOnlyTheListedScopesAsChanges() throws Exception {
        mvc.perform(patch("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":{\"TENANT\":\"READ\"}}"))
                .andExpect(status().isOk());

        UpdateApiKeyCommand command = (UpdateApiKeyCommand) received.getFirst();
        assertThat(command.permissions().levels()).containsExactly(java.util.Map.entry(Scope.TENANT, AccessLevel.READ));
    }

    @Test
    void gettingAKeyNeverExposesASecret() throws Exception {
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyId").value(KEY_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.lockVersion").doesNotExist())
                .andExpect(jsonPath("$.accessSecretHash").doesNotExist())
                .andExpect(jsonPath("$.accessSecret").doesNotExist());
    }

    @Test
    void patchLeavesOmittedFieldsNullInTheCommand() throws Exception {
        mvc.perform(patch("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed\"}"))
                .andExpect(status().isOk());

        UpdateApiKeyCommand command = (UpdateApiKeyCommand) received.getFirst();
        assertThat(command.name()).isEqualTo("renamed");
        assertThat(command.lifetime()).isNull();
        assertThat(command.permissions()).isNull();
    }

    @Test
    void deleteAndDismissReturn204AndSendTheirCommands() throws Exception {
        mvc.perform(delete("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/user/{userId}/apikey/{id}/dismiss", USER_ID, KEY_ID)).andExpect(status().isNoContent());

        assertThat(received).hasSize(2);
        assertThat(received.get(1)).isEqualTo(new DismissApiKeyCommand(USER_ID, KEY_ID));
    }

    @Test
    void notFoundBecomes404() throws Exception {
        failure = new ApiKeyNotFoundException();

        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void aVerificationFailureCarriesItsMachineCodeInTheProblemBody() throws Exception {
        failure = ApiKeyVerificationFailure.expired();
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KEY_EXPIRED"))
                .andExpect(jsonPath("$.detail").value("The API key has expired."));

        failure = ApiKeyVerificationFailure.malformed();
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_CREDENTIALS"));
    }

    @Test
    void aRefusedChangeBecomes409() throws Exception {
        failure = new ApiKeyUpdateFailure(UpdateFailureCode.API_KEY_DELETED, "Can't update a deleted API key");

        mvc.perform(patch("/api/v1/user/{userId}/apikey/{id}", USER_ID, KEY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isConflict());
    }
}
