package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import me.dhiren9939.backend.access.domain.aggregates.apikey.ChangeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyStatusTest {

    @Test
    void creationAndSettledStatesDoNotDependOnTheKind() {
        assertThat(ApiKeyStatus.of(ApiKeyState.PROVISIONING, ChangeKind.CREATE)).isEqualTo(ApiKeyStatus.CREATING);
        assertThat(ApiKeyStatus.of(ApiKeyState.PROVISIONING_FAILED, ChangeKind.CREATE)).isEqualTo(ApiKeyStatus.CREATION_FAILED);
        assertThat(ApiKeyStatus.of(ApiKeyState.ACTIVE, null)).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(ApiKeyStatus.of(ApiKeyState.EXPIRED, null)).isEqualTo(ApiKeyStatus.EXPIRED);
        assertThat(ApiKeyStatus.of(ApiKeyState.EXPIRED, ChangeKind.UPDATE)).as("a pending change does not change what is shown").isEqualTo(ApiKeyStatus.EXPIRED);
        assertThat(ApiKeyStatus.of(ApiKeyState.DELETED, null)).isEqualTo(ApiKeyStatus.DELETED);
    }

    @Test
    void aPendingChangeIsUpdatingOrDeletingByItsKind() {
        assertThat(ApiKeyStatus.of(ApiKeyState.CHANGE_PENDING, ChangeKind.UPDATE)).isEqualTo(ApiKeyStatus.UPDATING);
        assertThat(ApiKeyStatus.of(ApiKeyState.CHANGE_PENDING, ChangeKind.DELETE)).isEqualTo(ApiKeyStatus.DELETING);
    }

    @Test
    void aFailedChangeIsUpdateFailedOrDeleteFailedByItsKind() {
        assertThat(ApiKeyStatus.of(ApiKeyState.CHANGE_FAILED, ChangeKind.UPDATE)).isEqualTo(ApiKeyStatus.UPDATE_FAILED);
        assertThat(ApiKeyStatus.of(ApiKeyState.CHANGE_FAILED, ChangeKind.DELETE)).isEqualTo(ApiKeyStatus.DELETE_FAILED);
    }
}
