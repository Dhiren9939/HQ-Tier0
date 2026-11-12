package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.application.model.ApiKeyView;
import me.dhiren9939.backend.access.application.usecase.user.issue.IssuedApiKeyResult;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;
import me.dhiren9939.backend.shared.access.Permissions;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

    // ---- validation: the DTOs

    @Test
    void creatingWithInvalidFieldsListsEveryProblemAtOnce() throws Exception {
        mvc.perform(json(post(KEYS), "{\"name\":\"  \",\"permissions\":{\"INGESTION\":null}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='expiry')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='permissions[INGESTION]')]").exists());
        assertThat(received).isEmpty();
    }

    @Test
    void aTooLongOrControlCharacterNameIsRefused() throws Exception {
        mvc.perform(json(post(KEYS), "{\"name\":\"" + "x".repeat(257) + "\",\"expiry\":\"NEVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mvc.perform(json(post(KEYS), "{\"name\":\"line\\nbreak\",\"expiry\":\"NEVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not contain control characters"));
        assertThat(received).isEmpty();
    }

    @Test
    void updatingAcceptsEmptyAndPartialBodiesButNotABlankName() throws Exception {
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{}")).andExpect(status().isOk());
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{\"permissions\":{\"TENANT\":null}}")).andExpect(status().isOk());
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
        mvc.perform(json(patch(KEYS + "/" + KEY_ID), "{\"name\":\"" + "x".repeat(257) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theIssuerMustBeAStrictIdentifier() throws Exception {
        for (String answer : new String[]{CONFIRM, REJECT}) {
            for (String issuer : new String[]{"", " ", "-leading-dash", "has space", "bad\\nnewline", "semi;colon", "x".repeat(257)}) {
                mvc.perform(json(post(answer), "{\"issuer\":\"" + issuer + "\"}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.errors[0].field").value("issuer"));
            }
            mvc.perform(json(post(answer), "{\"issuer\":\"ingestion-service\"}")).andExpect(status().isNoContent());
            mvc.perform(json(post(answer), "{\"issuer\":\"Ingestion_1.v2:eu/west\"}")).andExpect(status().isNoContent());
        }
    }

    // ---- malformed requests

    @Test
    void invalidJsonAnEmptyBodyAndUnknownValuesAreMalformedRequests() throws Exception {
        mvc.perform(json(post(KEYS), "{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(KEYS).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"FOREVER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("FOREVER"))));
        mvc.perform(json(post(KEYS), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"NOPE\":\"READ\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(json(post(CONFIRM), "[]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(REJECT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(received).isEmpty();
    }

    @Test
    void aPathVariableOfTheWrongTypeNamesTheParameterButNotTheValue() throws Exception {
        mvc.perform(get("/api/v1/user/{userId}/apikey/{id}", "not-a-uuid", KEY_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("The value of 'userId' is not valid."))
                .andExpect(jsonPath("$.detail").value(not(containsString("not-a-uuid"))));
        mvc.perform(json(post("/internal/apikey/{id}/changes/{revision}/confirm", KEY_ID, "latest"), "{\"issuer\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("The value of 'revision' is not valid."));
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
    void domainErrorsCarryTheirCodesAndAreNotSwallowedByTheCatchAll() throws Exception {
        failure = new me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException();
        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));

        failure = new me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure("Can't update an API key with pending changes");
        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("API_KEY_CONFLICT"));

        failure = new me.dhiren9939.backend.shared.access.InvalidPermissionsException("nope");
        mvc.perform(get(KEYS + "/" + KEY_ID))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERMISSIONS"));
    }
}
