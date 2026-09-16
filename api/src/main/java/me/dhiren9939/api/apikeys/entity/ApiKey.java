package me.dhiren9939.api.apikeys.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID apiKeyId;

    private UUID userId;

    private String name;

    private String apiKeyHash;

    private Instant createdAt;

    private Instant expiresAt;

    public ApiKey(UUID userId, String name, String apiKeyHash, Instant createdAt, Instant expiresAt){
        this.userId = userId;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
