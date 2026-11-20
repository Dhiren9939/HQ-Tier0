package me.dhiren9939.backend.access.application.usecase.service.verify;

/**
 * Makes a caller-supplied identifier safe to log: every character outside {@code A-Z a-z 0-9 _ -} becomes {@code ?}
 * (so no line breaks or terminal escapes reach the log), and anything past {@value #MAX_LOGGED_LENGTH} characters is
 * cut off with its full length noted. Use it for every value the caller controls, even after it was parsed.
 */
final class LoggedIdentifier {

    /** Longest part of an identifier written to the log, in characters. A logging limit, not a key rule. */
    static final int MAX_LOGGED_LENGTH = 64;

    private LoggedIdentifier() {
    }

    static String of(String value) {
        if (value == null)
            return "<none>";

        int shown = Math.min(value.length(), MAX_LOGGED_LENGTH);
        StringBuilder safe = new StringBuilder(shown + 24);
        for (int i = 0; i < shown; i++) {
            char c = value.charAt(i);
            safe.append(isSafe(c) ? c : '?');
        }
        if (value.length() > MAX_LOGGED_LENGTH)
            safe.append("...(").append(value.length()).append(" chars)");
        return safe.toString();
    }

    private static boolean isSafe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }
}
