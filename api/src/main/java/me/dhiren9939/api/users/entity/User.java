package me.dhiren9939.api.users.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
@Id
    @GeneratedValue
    @UuidGenerator(style= UuidGenerator.Style.VERSION_7)
    private UUID userId;

    private String googleSub;

    private String firstName;

    private String lastName;

    private String email;
}
