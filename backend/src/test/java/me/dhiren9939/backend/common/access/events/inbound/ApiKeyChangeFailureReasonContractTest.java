package me.dhiren9939.backend.common.access.events.inbound;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The failure reason names are wire values the data plane sends. Renaming or reordering them is a contract
 * change and must fail here; translating them into the domain's own enum is the application's job.
 */
class ApiKeyChangeFailureReasonContractTest {

    @Test
    void namesAreTheWireValues() {
        assertThat(Arrays.stream(ApiKeyChangeFailureReason.values()).map(Enum::name))
                .containsExactly("REJECTED", "CONFIRM_TIMEOUT", "OUTBOX_PUBLISH_FAILURE", "CANCELLED_BY_USER");
    }

    @Test
    void rejectedEventCarriesTheReason() {
        UUID apiKeyId = UUID.randomUUID();
        ApiKeyChangeRejected rejected = new ApiKeyChangeRejected(apiKeyId, 2, "data-plane",
                ApiKeyChangeFailureReason.CONFIRM_TIMEOUT);

        assertThat(rejected.apiKeyId()).isEqualTo(apiKeyId);
        assertThat(rejected.version()).isEqualTo(2);
        assertThat(rejected.issuer()).isEqualTo("data-plane");
        assertThat(rejected.failureReason()).isEqualTo(ApiKeyChangeFailureReason.CONFIRM_TIMEOUT);
    }
}
