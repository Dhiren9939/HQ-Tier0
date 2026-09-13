package me.dhiren9939.api.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AuthCookieFactory;
import me.dhiren9939.api.auth.jwt.AuthCookies;
import me.dhiren9939.api.auth.jwt.JwtService;
import me.dhiren9939.api.auth.service.RefreshTokenService;
import me.dhiren9939.api.auth.service.RefreshTokenService.RotateResult;
import me.dhiren9939.api.common.ApiError;
import me.dhiren9939.api.common.ApiResponse;
import me.dhiren9939.api.users.entity.User;
import me.dhiren9939.api.users.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserService userService;

    /**
     * Called by the SPA when an API call 401s because the access token expired. Consumes the
     * refresh cookie exactly once - every response, success or failure, replaces or clears both
     * cookies, so the same refresh token is never usable twice.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request) {
        Optional<String> cookieValue = AuthCookies.read(request, AuthCookies.REFRESH_TOKEN);
        if (cookieValue.isEmpty()) {
            return unauthorized("REFRESH_TOKEN_INVALID", "Refresh token is missing or invalid.");
        }

        String deviceInfo = request.getHeader(HttpHeaders.USER_AGENT);
        RotateResult result = refreshTokenService.rotate(cookieValue.get(), deviceInfo);

        return switch (result) {
            case RotateResult.Rotated rotated -> onRotated(rotated);
            case RotateResult.Reused ignored -> unauthorized("REFRESH_TOKEN_REUSED",
                    "This refresh token was already used. All sessions have been signed out for security.");
            case RotateResult.Invalid ignored -> unauthorized("REFRESH_TOKEN_INVALID", "Refresh token is missing or invalid.");
        };
    }

    private ResponseEntity<ApiResponse<Void>> onRotated(RotateResult.Rotated rotated) {
        User user = userService.getById(rotated.userId());
        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getEmail());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.build(
                AuthCookies.ACCESS_TOKEN, accessToken, "/", jwtService.getAccessTokenTtl(), AuthCookies.ACCESS_TOKEN_SAME_SITE).toString());
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.build(
                AuthCookies.REFRESH_TOKEN, rotated.token().cookieValue(), AuthCookies.REFRESH_TOKEN_PATH,
                Duration.between(Instant.now(), rotated.token().expiresAt()), AuthCookies.REFRESH_TOKEN_SAME_SITE).toString());

        return ResponseEntity.ok().headers(headers).body(ApiResponse.of("Token refreshed.", null));
    }

    /**
     * Logs this device out only - other devices' sessions are untouched. Always succeeds and
     * always clears both cookies, whether or not a session was actually found, so a client
     * that's already logged out (or never was) gets the same clean result as one that wasn't.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        AuthCookies.read(request, AuthCookies.REFRESH_TOKEN).ifPresent(refreshTokenService::revoke);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.clear(AuthCookies.ACCESS_TOKEN, "/", AuthCookies.ACCESS_TOKEN_SAME_SITE).toString());
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.clear(AuthCookies.REFRESH_TOKEN, AuthCookies.REFRESH_TOKEN_PATH, AuthCookies.REFRESH_TOKEN_SAME_SITE).toString());

        return ResponseEntity.ok().headers(headers).body(ApiResponse.of("Logged out.", null));
    }

    private ResponseEntity<ApiResponse<Void>> unauthorized(String code, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.clear(AuthCookies.ACCESS_TOKEN, "/", AuthCookies.ACCESS_TOKEN_SAME_SITE).toString());
        headers.add(HttpHeaders.SET_COOKIE, AuthCookieFactory.clear(AuthCookies.REFRESH_TOKEN, AuthCookies.REFRESH_TOKEN_PATH, AuthCookies.REFRESH_TOKEN_SAME_SITE).toString());

        ApiResponse<Void> body = ApiResponse.fail(ApiError.of(401, code, message));
        return ResponseEntity.status(401).headers(headers).body(body);
    }
}
