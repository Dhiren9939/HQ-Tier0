package me.dhiren9939.api.auth.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Optional;

/** Cookie names shared between whatever issues them (success handler, refresh endpoint) and whatever reads them (filter). */
public final class AuthCookies {

    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";

    // The refresh cookie is scoped to this path so it's never sent on ordinary API calls -
    // only on the one endpoint that's allowed to see it.
    public static final String REFRESH_TOKEN_PATH = "/api/public/auth/refresh";

    public static final String ACCESS_TOKEN_SAME_SITE = "Lax";
    public static final String REFRESH_TOKEN_SAME_SITE = "Strict";

    private AuthCookies() {
    }

    public static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst();
    }
}
