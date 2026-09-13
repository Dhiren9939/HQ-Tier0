package me.dhiren9939.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dhiren9939.api.config.AppProperties;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Fires when the OAuth2 dance itself fails - consent denied, state/nonce mismatch, the
 * provider rejecting the code exchange, etc. Nothing was authenticated, so there are no
 * tokens/cookies to issue; this only decides what the browser sees next.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String ACCESS_DENIED = "access_denied";
    private static final String GENERIC_ERROR = "oauth_failed";

    private final AppProperties appProperties;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {

        // Full detail server-side only - the exception message can carry provider internals
        // (state values, redirect URIs) that shouldn't reach the browser.
        log.warn("OAuth2 login failed", exception);

        String errorCode = resolveErrorCode(exception);
        String redirectUrl = UriComponentsBuilder.fromUriString(appProperties.getFrontendLoginUrl())
                .queryParam("error", errorCode)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    /**
     * Only ever returns one of a fixed, small set of codes - never the raw exception message -
     * so the frontend gets just enough to show "you cancelled" vs a generic "something went
     * wrong", with no internal detail leaking through the redirect URL.
     */
    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception
                && oAuth2Exception.getError() != null
                && ACCESS_DENIED.equals(oAuth2Exception.getError().getErrorCode())) {
            return ACCESS_DENIED;
        }
        return GENERIC_ERROR;
    }
}
