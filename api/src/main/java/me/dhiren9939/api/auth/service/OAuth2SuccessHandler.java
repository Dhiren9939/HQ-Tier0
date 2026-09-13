package me.dhiren9939.api.auth.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.dhiren9939.api.auth.jwt.AuthCookieFactory;
import me.dhiren9939.api.auth.jwt.AuthCookies;
import me.dhiren9939.api.auth.jwt.JwtService;
import me.dhiren9939.api.config.AppProperties;
import me.dhiren9939.api.users.entity.User;
import me.dhiren9939.api.users.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        User user = userService.findOrCreateUser(oAuth2User);

        String accessToken = jwtService.generateAccessToken(user.getUserId(), user.getEmail());
        String deviceInfo = request.getHeader(HttpHeaders.USER_AGENT);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getUserId(), deviceInfo);

        addCookie(response, AuthCookies.ACCESS_TOKEN, accessToken, "/", jwtService.getAccessTokenTtl(), AuthCookies.ACCESS_TOKEN_SAME_SITE);
        addCookie(response, AuthCookies.REFRESH_TOKEN, refreshToken.cookieValue(), AuthCookies.REFRESH_TOKEN_PATH,
                Duration.between(Instant.now(), refreshToken.expiresAt()), AuthCookies.REFRESH_TOKEN_SAME_SITE);

        // Tokens travel only as cookies already set above - the redirect target carries nothing sensitive.
        response.sendRedirect(appProperties.getFrontendPostLoginUrl());
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge, String sameSite) {
        ResponseCookie cookie = AuthCookieFactory.build(name, value, path, maxAge, sameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
