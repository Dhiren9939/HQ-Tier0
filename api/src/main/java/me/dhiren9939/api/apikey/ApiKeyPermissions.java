package me.dhiren9939.api.apikey;

/**
 * Defines the permission levels that can be assigned to an API key.
 */
public class ApiKeyPermissions {
    /** Permission to submit ingestion data. */
    public enum Ingestion {
        NONE,
        WRITE
    }

    /** Permission to read or modify tenant resources. */
    public enum Tenant {
        NONE,
        READ,
        WRITE,
        READ_WRITE
    }

    /** Permission to read or modify channel resources. */
    public enum Channel {
        NONE,
        READ,
        WRITE,
        READ_WRITE
    }
}
