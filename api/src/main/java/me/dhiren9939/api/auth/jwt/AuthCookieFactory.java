package me.dhiren9939.api.auth.jwt;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * Builds the Set-Cookie value for auth cookies. Uses Spring's ResponseCookie (not jakarta's
 * Cookie) because it's the one that supports SameSite - jakarta.servlet.http.Cookie has no
 * such attribute.
 */
public final class AuthCookieFactory {

    private AuthCookieFactory() {
    }

    public static ResponseCookie build(String name, String value, String path, Duration maxAge, String sameSite) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true) // browsers exempt http://localhost from the Secure requirement, so this is safe in dev too
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    /** Tells the browser to delete the cookie immediately (maxAge 0), e.g. after an invalid/reused refresh. */
    public static ResponseCookie clear(String name, String path, String sameSite) {
        return build(name, "", path, Duration.ZERO, sameSite);
    }
}
