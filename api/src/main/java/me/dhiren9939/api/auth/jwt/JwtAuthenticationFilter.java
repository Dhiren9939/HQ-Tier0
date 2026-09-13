package me.dhiren9939.api.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the access-token cookie, verifies it via {@link JwtService}, and - if valid -
 * populates the SecurityContext for this request. No resource-server starter involved;
 * this is a plain servlet filter, same pattern as any other custom-token auth filter.
 *
 * Absence or invalidity of the cookie is not an error here: the request just proceeds
 * unauthenticated, and the authorization rules in SecurityConfig decide what happens next.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        AuthCookies.read(request, AuthCookies.ACCESS_TOKEN)
                .flatMap(jwtService::parseAccessToken)
                .ifPresent(claims -> authenticate(claims, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(AccessTokenClaims claims, HttpServletRequest request) {
        // Don't override an authentication already set upstream (e.g. mid oauth2Login callback).
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        var authToken = new UsernamePasswordAuthenticationToken(
                claims, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
