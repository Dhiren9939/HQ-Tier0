package me.dhiren9939.api.apikey.internal.version.v1;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.apikey.internal.ApiKeyCreationProvider;
import me.dhiren9939.api.apikey.internal.ApiKeyTriplet;
import me.dhiren9939.api.apikey.internal.version.Sha256SecretHasher;
import me.dhiren9939.api.apikey.internal.version.SortableEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service("creationV1")
@RequiredArgsConstructor
class ApiKeyCreationProviderV1 implements ApiKeyCreationProvider {
    public static final String KEY_VERSION = "V1";

    private final SortableEncoder sortableEncoder;
    private final SecureRandom secureRandom;
    private final Sha256SecretHasher secretHasher;

    @Override
    public ApiKeyTriplet createApiKeyTriplet() {
        StringBuilder accessIdBuilder = new StringBuilder();
        accessIdBuilder.append(KEY_VERSION).append("_");

        UUID accessUUID = UuidCreator.getTimeOrderedEpoch();
        String sortedBase64Uuid = sortableEncoder
                .encode(new long[]{accessUUID.getMostSignificantBits(), accessUUID.getLeastSignificantBits()});
        accessIdBuilder.append(sortedBase64Uuid);

        long[] accessSecretLongs = new long[4];
        for(int i = 0; i<accessSecretLongs.length;i++)
            accessSecretLongs[i] = secureRandom.nextLong();

        String accessSecret = sortableEncoder.encode(accessSecretLongs);

        return new ApiKeyTriplet(accessIdBuilder.toString(), accessSecret, secretHasher.hashHex(accessSecret));
    }
}
