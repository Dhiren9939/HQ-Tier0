package me.dhiren9939.backend.access.infrastructure.keyformat.v1;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.application.interfaces.CredentialVerifier;
import me.dhiren9939.backend.access.infrastructure.keyformat.Sha256SecretHasher;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
class V1CredentialVerifier implements CredentialVerifier {
    private final Sha256SecretHasher secretHasher;

    @Override
    public boolean supports(String keyFormat) {
        return V1KeyShape.KEY_FORMAT.equalsIgnoreCase(keyFormat);
    }

    @Override
    public boolean isWellFormed(String accessId, String accessSecret) {
        return V1KeyShape.isWellFormed(accessId, accessSecret);
    }

    @Override
    public boolean matches(String presentedSecret, String storedSecretHash) {
        byte[] stored;
        try {
            stored = HexFormat.of().parseHex(storedSecretHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
        return MessageDigest.isEqual(secretHasher.hash(presentedSecret), stored);
    }
}
