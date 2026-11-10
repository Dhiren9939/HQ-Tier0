package me.dhiren9939.backend.access.adapter.out.keyformat.v1;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.adapter.out.keyformat.CredentialIssuer;
import me.dhiren9939.backend.access.adapter.out.keyformat.Sha256SecretHasher;
import me.dhiren9939.backend.access.adapter.out.keyformat.SortableEncoder;
import me.dhiren9939.backend.access.domain.IssuedCredential;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class V1CredentialIssuer implements CredentialIssuer {
    static final String KEY_FORMAT = "V1";

    private final SortableEncoder sortableEncoder;
    private final SecureRandom secureRandom;
    private final Sha256SecretHasher secretHasher;

    @Override
    public String keyFormat() {
        return KEY_FORMAT;
    }

    @Override
    public IssuedCredential issue() {
        UUID accessUuid = UuidCreator.getTimeOrderedEpoch();
        String accessId = KEY_FORMAT + "_" + sortableEncoder
                .encode(new long[]{accessUuid.getMostSignificantBits(), accessUuid.getLeastSignificantBits()});

        long[] secretLongs = new long[4];
        for (int i = 0; i < secretLongs.length; i++)
            secretLongs[i] = secureRandom.nextLong();

        String accessSecret = sortableEncoder.encode(secretLongs);

        return new IssuedCredential(accessId, accessSecret, secretHasher.hashHex(accessSecret));
    }
}
