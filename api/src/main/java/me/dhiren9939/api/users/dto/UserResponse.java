package me.dhiren9939.api.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.dhiren9939.api.users.entity.User;

import java.util.UUID;

/** Never serialize the User entity directly - this controls exactly what a client can see. */
@Schema(description = "The signed-in user's profile.")
public record UserResponse(
        @Schema(description = "The user's id.") UUID userId,
        @Schema(description = "The user's email address.", example = "jane@example.com") String email,
        @Schema(description = "The user's first name.", example = "Jane") String firstName,
        @Schema(description = "The user's last name.", example = "Doe") String lastName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
