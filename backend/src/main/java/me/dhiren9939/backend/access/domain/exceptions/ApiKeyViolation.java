package me.dhiren9939.backend.access.domain.exceptions;

/**
 * A rule a caller broke when asking to issue or change a key. The name is the machine-readable code clients see;
 * where a request DTO checks the same rule, it uses the same code, so a client gets one code whichever layer
 * caught it.
 */
public enum ApiKeyViolation {
    USER_REQUIRED("userId", "The owner of the key is required."),
    NAME_REQUIRED("name", "The name is required."),
    NAME_BLANK("name", "The name must not be blank."),
    NAME_TOO_LONG("name", "The name is too long."),
    NAME_INVALID_CHARACTERS("name", "The name must not contain control characters."),
    EXPIRY_REQUIRED("expiry", "The lifetime of the key is required."),
    PERMISSIONS_REQUIRED("permissions", "The permissions are required; grant none for no access."),
    PERMISSION_SCOPE_REQUIRED("permissions", "A permission scope must not be null."),
    INVALID_SCOPE_LEVEL("permissions", "A permission level is not allowed for its scope.");

    private final String field;
    private final String message;

    ApiKeyViolation(String field, String message) {
        this.field = field;
        this.message = message;
    }

    /** The request field the rule is about. */
    public String field() {
        return field;
    }

    /** A message safe to show to an end user; it never repeats the value that was sent. */
    public String message() {
        return message;
    }
}
