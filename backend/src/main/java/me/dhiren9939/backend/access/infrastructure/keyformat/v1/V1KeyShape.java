package me.dhiren9939.backend.access.infrastructure.keyformat.v1;

import me.dhiren9939.backend.access.infrastructure.keyformat.SortableEncoder;

/**
 * What a V1 credential looks like, in one place for the issuer that creates it and the verifier that checks it.
 *
 * <ul>
 *   <li>Access ID: {@code V1_} and a time-ordered UUID (2 longs) in the sortable alphabet: 3 + 22 = 25 characters.</li>
 *   <li>Secret: 256 random bits (4 longs) in the sortable alphabet: 43 characters.</li>
 * </ul>
 */
final class V1KeyShape {
    static final String KEY_FORMAT = "V1";
    static final String ACCESS_ID_PREFIX = KEY_FORMAT + "_";

    /** The access id encodes a time-ordered UUID: two longs. */
    static final int ACCESS_ID_LONGS = 2;
    /** The secret is 256 random bits: four longs. */
    static final int SECRET_LONGS = 4;

    static final int ACCESS_ID_LENGTH = ACCESS_ID_PREFIX.length() + SortableEncoder.encodedLength(ACCESS_ID_LONGS);
    static final int SECRET_LENGTH = SortableEncoder.encodedLength(SECRET_LONGS);

    private V1KeyShape() {
    }

    /**
     * True only for a credential V1 could have issued. The lengths are compared first, so input of any size is
     * refused at once and never reaches the lookup or the hash.
     */
    static boolean isWellFormed(String accessId, String accessSecret) {
        return accessId.length() == ACCESS_ID_LENGTH
                && accessSecret.length() == SECRET_LENGTH
                && accessId.startsWith(ACCESS_ID_PREFIX)
                && SortableEncoder.isEncoded(accessId.substring(ACCESS_ID_PREFIX.length()))
                && SortableEncoder.isEncoded(accessSecret);
    }
}
