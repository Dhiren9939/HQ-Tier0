package me.dhiren9939.backend.access.adapter.out.keyformat.v1;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.backend.access.adapter.out.keyformat.Sha256SecretHasher;
import me.dhiren9939.backend.access.application.port.out.CredentialVerifier;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
class V1CredentialVerifier implements CredentialVerifier {
    private final Sha256SecretHasher secretHasher;

    @Override
    public boolean supports(String keyFormat) {
        return V1CredentialIssuer.KEY_FORMAT.equalsIgnoreCase(keyFormat);
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
