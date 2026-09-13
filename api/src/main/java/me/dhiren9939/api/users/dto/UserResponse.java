package me.dhiren9939.api.users.dto;

import me.dhiren9939.api.users.entity.User;

import java.util.UUID;

/** Never serialize the User entity directly - this controls exactly what a client can see. */
public record UserResponse(UUID userId, String email, String firstName, String lastName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
