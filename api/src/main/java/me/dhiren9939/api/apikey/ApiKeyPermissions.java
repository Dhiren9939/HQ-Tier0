package me.dhiren9939.api.apikey;

public class ApiKeyPermissions {
    public enum Ingestion {
        NONE,
        WRITE
    }

    public enum Tenant {
        NONE,
        READ,
        WRITE,
        READ_WRITE
    }

    public enum Channel {
        NONE,
        READ,
        WRITE,
        READ_WRITE
    }
}
