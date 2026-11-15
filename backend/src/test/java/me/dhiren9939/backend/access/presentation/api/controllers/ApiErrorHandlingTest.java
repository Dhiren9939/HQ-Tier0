package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.common.access.VerificationFailureCode;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;
import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.exceptions.InvalidPermissionsException;
import me.dhiren9939.backend.access.domain.exceptions.UpdateFailureCode;
import me.dhiren9939.backend.web.errors.WebExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Every way a request can be wrong ends in the same problem body with a machine code, and nothing the caller
 * sent or the server threw is echoed back.
 */
class ApiErrorHandlingTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ApiKeyView VIEW = new ApiKeyView(KEY_ID, USER_ID, "V1_acc", "test-key", Permissions.none(),
            Instant.parse("2026-01-15T12:00:00Z"), null, ApiKeyState.PROVISIONING, ChangeKind.CREATE);

    private final List<Object> received = new ArrayList<>();
    private RuntimeException failure;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ApiKeyController(
                                command -> {
                                    received.add(command);
                                    return new IssuedApiKeyResult(VIEW, "the-secret");
                                },
                                query -> {
                                    if (failure != null) throw failure;
                                    return VIEW;
                                },
                                command -> {
                                    received.add(command);
                                    return VIEW;
                                },
                                command -> received.add(command),
                                command -> received.add(command),
                                command -> received.add(command)),
                        new ApiKeyInternalController(command -> received.add(command), command -> received.add(command)))
                .setControllerAdvice(new ApiKeyExceptionHandler(), new WebExceptionHandler())
                .build();
    }

    private static final String KEYS = "/api/v1/user/" + USER_ID + "/apikey";
    private static final String CONFIRM = "/internal/apikey/" + KEY_ID + "/changes/1/confirm";
    private static final String REJECT = "/internal/apikey/" + KEY_ID + "/changes/1/reject";

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- validation: the DTOs, one code per constraint

    private void assertFieldError(MockHttpServletRequestBuilder request, String field, String code) throws Exception {
        received.clear();
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "' && @.code=='" + code + "')]").exists())
                .andExpect(jsonPath("$.errors[*].message").value(not(hasItem(startsWith("{")))));
        assertThat(received).isEmpty();
    }

    @Test
    void creatingWithInvalidFieldsListsEveryProblemWithItsOwnCode() throws Exception {
        mvc.perform(json(post(KEYS), "{\"name\":\"  \",\"permissions\":{\"INGESTION\":null}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field=='name' && @.code=='NAME_REQUIRED')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field=='expiry' && @.code=='EXPIRY_REQUIRED')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='permissions[INGESTION]' && @.code=='PERMISSION_LEVEL_REQUIRED')]").exists());
        assertThat(received).isEmpty();
    }

    @Test
    void everyNameConstraintHasItsOwnCode() throws Exception {
        assertFieldError(json(post(KEYS), "{\"name\":\"\",\"expiry\":\"NEVER\"}"), "name", "NAME_REQUIRED");
        assertFieldError(json(post(KEYS), "{\"name\":\"" + "x".repeat(257) + "\",\"expiry\":\"NEVER\"}"), "name", "NAME_TOO_LONG");
        assertFieldError(json(post(KEYS), "{\"name\":\"line\\nbreak\",\"expiry\":\"NEVER\"}"), "name", "NAME_INVALID_CHARACTERS");
        assertFieldError(json(patch(KEYS + "/" + KEY_ID), "{\"name\":\"   \"}"), "name", "NAME_BLANK");
        assertFieldError(json(patch(KEYS + "/" + KEY_ID), "{\"name\":\"" + "x".repeat(257) + "\"}"), "name", "NAME_TOO_LONG");
        assertFieldError(json(patch(KEYS + "/" + KEY_ID), "{\"name\":\"tab\\there\"}"), "name", "NAME_INVALID_CHARACTERS");
    }

    @Test
    void theTooLongMessageSaysTheLimit() throws Exception {
        mvc.perform(json(post(KEYS), "{\"name\":\"" + "x".repeat(257) + "\",\"expiry\":\"NEVER\"}"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 256 characters"));
    }

    @Test
    void updatingAcceptsEmptyAndPartialBodies() throws Exception {
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{}")).andExpect(status().isOk());
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{\"permissions\":{\"TENANT\":null}}")).andExpect(status().isOk());
    }

    @Test
    void everyIssuerConstraintHasItsOwnCode() throws Exception {
        for (String answer : new String[]{CONFIRM, REJECT}) {
            assertFieldError(json(post(answer), "{\"issuer\":\"\"}"), "issuer", "ISSUER_REQUIRED");
            assertFieldError(json(post(answer), "{\"issuer\":\" \"}"), "issuer", "ISSUER_REQUIRED");
            assertFieldError(json(post(answer), "{}"), "issuer", "ISSUER_REQUIRED");
            assertFieldError(json(post(answer), "{\"issuer\":\"" + "x".repeat(257) + "\"}"), "issuer", "ISSUER_TOO_LONG");
            for (String bad : new String[]{"-leading-dash", "has space", "bad\\nnewline", "semi;colon"})
                assertFieldError(json(post(answer), "{\"issuer\":\"" + bad + "\"}"), "issuer", "ISSUER_INVALID_FORMAT");
            mvc.perform(json(post(answer), "{\"issuer\":\"ingestion-service\"}")).andExpect(status().isNoContent());
            mvc.perform(json(post(answer), "{\"issuer\":\"Ingestion_1.v2:eu/west\"}")).andExpect(status().isNoContent());
        }
    }

    // ---- malformed requests: one code per cause

    private void assertBadBody(MockHttpServletRequestBuilder request, String code) throws Exception {
        received.clear();
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertThat(received).isEmpty();
    }

    @Test
    void eachWayABodyCanBeUnreadableHasItsOwnCode() throws Exception {
        assertBadBody(json(post(KEYS), "{not json"), "REQUEST_BODY_NOT_JSON");
        assertBadBody(post(KEYS).contentType(MediaType.APPLICATION_JSON), "REQUEST_BODY_MISSING");
        assertBadBody(post(REJECT).contentType(MediaType.APPLICATION_JSON), "REQUEST_BODY_MISSING");
        assertBadBody(json(post(CONFIRM), "[]"), "REQUEST_BODY_WRONG_TYPE");
    }

    @Test
    void anUnknownValueIsNamedAfterWhatCouldNotBeRead() throws Exception {
        assertBadBody(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"FOREVER\"}"), "INVALID_LIFETIME");
        assertBadBody(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"NOPE\":\"READ\"}}"), "INVALID_SCOPE");
        assertBadBody(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"INGESTION\":\"SUPER\"}}"), "INVALID_ACCESS_LEVEL");
        assertBadBody(json(patch(KEYS + "/" + KEY_ID), "{\"expiry\":\"FOREVER\"}"), "INVALID_LIFETIME");
        assertBadBody(json(post(REJECT), "{\"issuer\":\"a\",\"reason\":\"BORED\"}"), "INVALID_API_KEY_CHANGE_FAILURE_REASON");
    }

    @Test
    void theBadValueIsNeverEchoedButTheFieldIsNamed() throws Exception {
        mvc.perform(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"FOREVER\"}"))
                .andExpect(jsonPath("$.field").value("expiry"))
                .andExpect(content().string(not(containsString("FOREVER"))));
        mvc.perform(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"NOPE\":\"READ\"}}"))
                .andExpect(jsonPath("$.field").value("permissions"))
                .andExpect(content().string(not(containsString("NOPE"))));
    }

    @Test
    void aPathVariableOfTheWrongTypeIsNamedAfterTheParameter() throws Exception {
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", "not-a-uuid", KEY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_USER_ID"))
                .andExpect(jsonPath("$.parameter").value("userId"))
                .andExpect(jsonPath("$.detail").value(not(containsString("not-a-uuid"))));
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", USER_ID, "nope"))
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY_ID"));
        mvc.perform(json(post("/internal/apikey/{id}/changes/{revision}/confirm", KEY_ID, "latest"), "{\"issuer\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REVISION"));
    }

    // ---- transport errors

    @Test
    void aWrongMethodIs405WithTheAllowHeader() throws Exception {
        mvc.perform(put(KEYS + "/" + KEY_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void aWrongOrMissingContentTypeIs415() throws Exception {
        mvc.perform(post(KEYS).contentType(MediaType.TEXT_PLAIN).content("name=k"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(CONFIRM).content("{\"issuer\":\"a\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anUnacceptableAcceptHeaderIs406() throws Exception {
        mvc.perform(get(KEYS + "/" + KEY_ID).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    // ---- unexpected errors and the domain errors keep their own codes

    @Test
    void anUnexpectedErrorIs500WithAGenericBodyAndNoDetailLeaked() throws Exception {
        failure = new IllegalStateException("connection to db-prod-7 refused");

        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("db-prod-7"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void domainErrorsCarryTheirOwnCodesAndAreNotSwallowedByTheCatchAll() throws Exception {
        failure = new me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException();
        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));

        for (UpdateFailureCode code : UpdateFailureCode.values()) {
            failure = new ApiKeyUpdateFailure(code, "refused");
            mvc.perform(get(KEYS + "/" + KEY_ID))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code.name()));
        }

        failure = new InvalidPermissionsException("nope");
        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCOPE_LEVEL"));

        for (VerificationFailureCode code : VerificationFailureCode.values()) {
            failure = new ApiKeyVerificationFailure(code, "refused");
            mvc.perform(get(KEYS + "/" + KEY_ID))
                    .andExpect(jsonPath("$.code").value(code.name()));
        }
    }
}
