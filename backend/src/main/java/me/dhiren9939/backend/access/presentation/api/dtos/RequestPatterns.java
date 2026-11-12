package me.dhiren9939.backend.access.presentation.api.dtos;

/** Shared validation patterns for request bodies. */
final class RequestPatterns {

    /** Free text such as a key name: anything except control characters (newlines, tabs, escapes). */
    static final String NO_CONTROL_CHARACTERS = "[^\\p{Cntrl}]*";

    /** At least one character that is not whitespace. */
    static final String NOT_BLANK_TEXT = "(?s).*\\S.*";

    /**
     * An identifier for a calling service, such as {@code ingestion-service}: it starts with a letter or digit and
     * continues with letters, digits and {@code . _ : / -}. Strict on purpose, because it is written to logs.
     */
    static final String SERVICE_IDENTIFIER = "[A-Za-z0-9][A-Za-z0-9._:/-]*";

    private RequestPatterns() {
    }
}
