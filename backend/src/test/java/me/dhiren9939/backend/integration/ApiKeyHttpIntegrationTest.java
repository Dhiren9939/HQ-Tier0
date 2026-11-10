package me.dhiren9939.backend.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP surface with the real beans and a real PostgreSQL behind it. */
class ApiKeyHttpIntegrationTest extends AccessIntegrationTest {

    @Autowired MockMvc mvc;

    private final UUID owner = UUID.randomUUID();

    private String keys() {
        return "/api/v1/user/" + owner + "/apikey";
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request);
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID create(String body) throws Exception {
        String response = json(post(keys()), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.apiKeyId"));
    }

    @Test
    @DisplayName("full lifecycle over HTTP: create, read, confirm, patch, conflict, reject, dismiss, delete, tombstone")
    void lifecycle() throws Exception {
        String created = json(post(keys()), """
                {"name":"deploy","expiry":"ONE_DAY","permissions":{"INGESTION":"WRITE"}}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessSecret").isNotEmpty())
                .andExpect(jsonPath("$.accessId").value(org.hamcrest.Matchers.startsWith("V1_")))
                .andExpect(jsonPath("$.state").value("PROVISIONING"))
                .andExpect(jsonPath("$.updateStatus").value("PENDING"))
                .andExpect(jsonPath("$.permissions.INGESTION").value("WRITE"))
                .andExpect(jsonPath("$.permissions.TENANT").value("NONE"))
                .andExpect(jsonPath("$.permissions.CHANNEL").value("NONE"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.apiKeyId"));
        String key = keys() + "/" + id;
        String internal = "/internal/apikey/" + id + "/changes/";

        send(get(key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessSecret").doesNotExist())
                .andExpect(jsonPath("$.state").value("PROVISIONING"))
                .andExpect(jsonPath("$.apiKeyName").value("deploy"));

        send(post(internal + "1/confirm")).andExpect(status().isNoContent());
        send(get(key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.updateStatus").doesNotExist());

        json(patch(key), """
                {"name":"renamed","permissions":{"TENANT":"READ"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CHANGE_PENDING"))
                .andExpect(jsonPath("$.apiKeyName").value("deploy"));

        json(patch(key), "{\"name\":\"again\"}").andExpect(status().isConflict());
        send(post(internal + "1/confirm")).andExpect(status().isConflict());
        send(delete(key)).andExpect(status().isConflict());

        json(post(internal + "2/reject"), "{\"reason\":\"CONFIRM_TIMEOUT\"}").andExpect(status().isNoContent());
        send(get(key)).andExpect(jsonPath("$.state").value("CHANGE_FAILED"))
                .andExpect(jsonPath("$.updateStatus").value("FAILED"));
        assertThat(jdbc.queryForObject("select failure_reason from access.pending_changes", String.class))
                .isEqualTo("CONFIRM_TIMEOUT");

        send(post(key + "/dismiss")).andExpect(status().isNoContent());
        send(get(key)).andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.apiKeyName").value("deploy"));

        json(patch(key), "{\"name\":\"renamed\",\"expiry\":\"ONE_MONTH\"}").andExpect(status().isOk());
        send(post(internal + "3/confirm")).andExpect(status().isNoContent());
        send(get(key)).andExpect(jsonPath("$.apiKeyName").value("renamed"))
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        send(delete(key)).andExpect(status().isNoContent());
        send(post(internal + "4/reject")).andExpect(status().isNoContent()); // body omitted -> REJECTED
        assertThat(jdbc.queryForObject("select failure_reason from access.pending_changes", String.class))
                .isEqualTo("REJECTED");
        send(post(key + "/dismiss")).andExpect(status().isNoContent());

        send(delete(key)).andExpect(status().isNoContent());
        send(post(internal + "5/confirm")).andExpect(status().isNoContent());
        send(get(key)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DELETED"));
        json(patch(key), "{\"name\":\"zombie\"}").andExpect(status().isConflict());

        int events = outbox().size();
        send(post(key + "/dismiss")).andExpect(status().isNoContent());
        send(get(key)).andExpect(status().isNotFound());
        assertThat(outbox()).hasSize(events);
        assertThat(outbox()).extracting(OutboxRow::eventType).containsExactly(
                "apiKey.created", "apiKey.updated", "apiKey.updated", "apiKey.deleted", "apiKey.deleted");
    }

    @Test
    @DisplayName("404: unknown key, someone else's key, unknown key on the internal endpoints")
    void notFound() throws Exception {
        UUID id = create("{\"name\":\"k\",\"expiry\":\"NEVER\"}");
        UUID stranger = UUID.randomUUID();

        send(get(keys() + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        send(get("/api/v1/user/" + stranger + "/apikey/" + id)).andExpect(status().isNotFound());
        json(patch("/api/v1/user/" + stranger + "/apikey/" + id), "{\"name\":\"x\"}").andExpect(status().isNotFound());
        send(delete("/api/v1/user/" + stranger + "/apikey/" + id)).andExpect(status().isNotFound());
        send(post("/internal/apikey/" + UUID.randomUUID() + "/changes/1/confirm")).andExpect(status().isNotFound());
        send(post("/internal/apikey/" + UUID.randomUUID() + "/changes/1/reject")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("409: a key that is still provisioning cannot be changed or deleted over HTTP")
    void conflictWhileProvisioning() throws Exception {
        UUID id = create("{\"name\":\"k\",\"expiry\":\"NEVER\"}");
        String key = keys() + "/" + id;

        // a provisioning key is reported as not found for changes (the owner has nothing to change yet)
        json(patch(key), "{\"name\":\"x\"}").andExpect(status().isNotFound());
        send(delete(key)).andExpect(status().isNotFound());
        send(post(key + "/dismiss")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("400: a level the scope does not allow, unknown scope, unknown level, malformed body")
    void badRequests() throws Exception {
        json(post(keys()), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"INGESTION\":\"READ\"}}")
                .andExpect(status().isBadRequest());
        json(post(keys()), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"NOPE\":\"READ\"}}")
                .andExpect(status().isBadRequest());
        json(post(keys()), "{\"name\":\"k\",\"expiry\":\"NEVER\",\"permissions\":{\"TENANT\":\"SUPER\"}}")
                .andExpect(status().isBadRequest());
        json(post(keys()), "{\"name\":\"k\",\"expiry\":\"FOREVER\"}").andExpect(status().isBadRequest());
        json(post(keys()), "{not json").andExpect(status().isBadRequest());

        UUID id = create("{\"name\":\"k\",\"expiry\":\"NEVER\"}");
        send(post("/internal/apikey/" + id + "/changes/1/confirm")).andExpect(status().isNoContent());
        json(patch(keys() + "/" + id), "{\"permissions\":{\"INGESTION\":\"READ_WRITE\"}}")
                .andExpect(status().isBadRequest());
        json(patch(keys() + "/" + id), "{\"name\":\"\"}").andExpect(status().isBadRequest());
        json(patch(keys() + "/" + id), "{\"name\":\"" + "x".repeat(257) + "\"}").andExpect(status().isBadRequest());
        json(post(keys()), "{\"name\":\"" + "x".repeat(257) + "\",\"expiry\":\"NEVER\"}")
                .andExpect(status().isBadRequest());
        assertThat(count("access.outbox")).as("rejected requests write no events").isEqualTo(1);
    }

    @Test
    @DisplayName("400: a create request without a name or an expiry is a client error, not a 500")
    void missingRequiredFields() throws Exception {
        json(post(keys()), "{\"name\":\"no expiry\"}").andExpect(status().isBadRequest());
        json(post(keys()), "{\"expiry\":\"NEVER\"}").andExpect(status().isBadRequest());
        json(post(keys()), "{}").andExpect(status().isBadRequest());
        assertThat(count("access.api_keys")).isZero();
    }

    @Test
    @DisplayName("401 and 400 reach the client from verification failures (via the test probe endpoint)")
    void verificationErrors() throws Exception {
        String created = json(post(keys()), "{\"name\":\"k\",\"expiry\":\"NEVER\"}").andReturn()
                .getResponse().getContentAsString();
        String accessId = JsonPath.read(created, "$.accessId");
        String secret = JsonPath.read(created, "$.accessSecret");
        UUID id = UUID.fromString(JsonPath.read(created, "$.apiKeyId"));

        send(get("/test-probe/verify").param("accessId", accessId).param("secret", secret))
                .andExpect(status().isUnauthorized()); // still provisioning
        send(post("/internal/apikey/" + id + "/changes/1/confirm")).andExpect(status().isNoContent());

        send(get("/test-probe/verify").param("accessId", accessId).param("secret", secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyId").value(id.toString()))
                .andExpect(jsonPath("$.userId").value(owner.toString()));
        send(get("/test-probe/verify").param("accessId", accessId).param("secret", "wrong"))
                .andExpect(status().isUnauthorized());
        send(get("/test-probe/verify").param("accessId", "V1_unknown").param("secret", secret))
                .andExpect(status().isUnauthorized());
        send(get("/test-probe/verify").param("accessId", "V9_unsupportedFormat").param("secret", secret))
                .andExpect(status().isUnauthorized());
        send(get("/test-probe/verify").param("accessId", "not-an-access-id").param("secret", secret))
                .andExpect(status().isBadRequest());
    }
}
