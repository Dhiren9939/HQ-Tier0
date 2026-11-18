package me.dhiren9939.backend.access.presentation.api.dtos;

import me.dhiren9939.backend.access.domain.aggregates.apikey.ApiKeyState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyStatusTest {

    @Test
    void eachInternalStateHasItsOwnStatus() {
        assertThat(ApiKeyStatus.of(ApiKeyState.ACTIVE)).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(ApiKeyStatus.of(ApiKeyState.EXPIRED)).isEqualTo(ApiKeyStatus.EXPIRED);
    }
}
