package me.dhiren9939.api.users.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.common.ApiResponseDto;
import me.dhiren9939.api.users.dto.UserResponse;
import me.dhiren9939.api.users.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not under /api/v1/public/** - reaching this endpoint requires JwtAuthenticationFilter to have
 * populated the SecurityContext from a valid access-token cookie; otherwise JsonAuthenticationEntryPoint
 * answers with a 401 before this method ever runs. This is what the SPA calls on load to find out
 * whether it's logged in and who as.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Profile", description = "The signed-in user's own profile.")
public class ProfileController {

    private final UserService userService;

    @GetMapping("/api/v1/profile")
    @Operation(summary = "Get current user", description = "Returns the profile of the signed-in user, identified by the access_token cookie.")
    @ApiResponse(responseCode = "200", description = "Profile returned.")
    @ApiResponse(responseCode = "401", description = "Not signed in, or the access token is missing/invalid.")
    public ApiResponseDto<UserResponse> profile(@AuthenticationPrincipal AccessTokenClaims claims) {
        UserResponse user = UserResponse.from(userService.getById(claims.userId()));
        return ApiResponseDto.of(user);
    }
}
