package com.example.recipemanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads a {@code Bearer} token off every request and, when valid, populates
 * the {@link SecurityContextHolder} with a {@link UserPrincipal}. A
 * missing/invalid token is not itself an error here — it just leaves the
 * request unauthenticated; {@code SecurityConfig}'s {@code authorizeHttpRequests}
 * is what turns "unauthenticated" into a 401 for protected paths.
 * <p>
 * A token can pass {@code jwtService.isTokenValid} (well-formed, correctly
 * signed, unexpired) yet still fail to resolve to an existing user — e.g. the
 * account was deleted after the token was issued. {@code loadUserByUsername}
 * throwing in that case is caught here explicitly: this filter runs ahead of
 * {@code ExceptionTranslationFilter} in the chain, so an uncaught exception
 * would otherwise surface as a raw 500 instead of the API's usual
 * {@code ProblemDetail} 401. On that failure the chain is not continued —
 * {@link #authenticationEntryPoint} writes the 401 directly.
 * <p>
 * Deliberately <strong>not</strong> a {@code @Component}: {@code @WebMvcTest}
 * slices always let {@code Filter}-typed beans through their component-scan
 * filter (see AUTHENTICATION_SPEC.md's baseline note on this), so a
 * stereotype-annotated filter here would get instantiated inside e.g.
 * {@code RecipeControllerTest}'s slice even though its dependencies
 * ({@link JwtService}, {@link UserDetailsServiceImpl}) are plain
 * {@code @Service} beans that slice excludes — failing that context's
 * startup. {@code SecurityConfig} constructs this directly instead.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            if (jwtService.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    String username = jwtService.extractUsername(token);
                    UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (RuntimeException e) {
                    SecurityContextHolder.clearContext();
                    AuthenticationException authException =
                            new InsufficientAuthenticationException("Token subject could not be authenticated", e);
                    authenticationEntryPoint.commence(request, response, authException);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
