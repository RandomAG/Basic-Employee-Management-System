package net.emsayush.ems_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides client-side access to the currently authenticated user's identity
 * and granted authorities so the frontend can apply role-based UI rules.
 *
 * <p>The endpoint is session-based: the browser sends the {@code JSESSIONID}
 * cookie automatically, Spring Security resolves the {@link Authentication},
 * and we map it to a simple JSON payload.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Returns the username and roles of the currently authenticated principal.
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "username": "admin",
     *   "roles":    ["ROLE_ADMIN"],
     *   "isAdmin":  true
     * }
     * }</pre>
     *
     * @param authentication injected by Spring Security — {@code null} only if
     *                       the endpoint is somehow reached unauthenticated (which
     *                       the SecurityFilterChain prevents).
     * @return 200 with user info, or 401 if unauthenticated
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", authentication.getName());
        payload.put("roles",    roles);
        payload.put("isAdmin",  roles.contains("ROLE_ADMIN"));

        return ResponseEntity.ok(payload);
    }
}
