package me.dhiren9939.api.users.controller;

import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AccessTokenClaims;
import me.dhiren9939.api.common.ApiResponse;
import me.dhiren9939.api.users.dto.UserResponse;
import me.dhiren9939.api.users.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not under /api/public/** - reaching this endpoint requires JwtAuthenticationFilter to have
 * populated the SecurityContext from a valid access-token cookie; otherwise JsonAuthenticationEntryPoint
 * answers with a 401 before this method ever runs. This is what the SPA calls on load to find out
 * whether it's logged in and who as.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping("/api/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AccessTokenClaims claims) {
        UserResponse user = UserResponse.from(userService.getById(claims.userId()));
        return ApiResponse.of(user);
    }
}
