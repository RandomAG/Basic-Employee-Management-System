package net.emsayush.ems_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security configuration — RBAC + Form Login + JSON error responses.
 *
 * <h2>Root cause fix for dashboard "Loading…" hang</h2>
 * <p>Spring Security's default {@code ExceptionTranslationFilter} responds to
 * unauthenticated requests with a <strong>302 HTML redirect</strong> to the
 * login page. The browser's Fetch API follows that redirect automatically and
 * returns the login page HTML with status 200, making {@code res.ok} appear
 * {@code true}. When the JS then calls {@code res.json()} it throws a
 * {@link com.fasterxml.jackson.core.JsonParseException} and the catch block
 * redirects back to login — or, if the session IS valid but some other
 * path triggers the entry point, the spinner never clears.
 *
 * <p>The fix: register a custom {@link AuthenticationEntryPoint} for all
 * {@code /api/**} paths that returns a proper {@code 401 JSON} payload
 * instead of an HTML redirect. The JS can then detect {@code res.status === 401}
 * and cleanly redirect to the login page.
 *
 * <h2>RBAC Matrix</h2>
 * <pre>
 * ┌────────────────────────────────────┬────────┬──────┐
 * │ Endpoint                           │ ADMIN  │ USER │
 * ├────────────────────────────────────┼────────┼──────┤
 * │ GET  /api/employees/**             │  ✅   │  ✅  │
 * │ POST /api/employees                │  ✅   │  ❌  │
 * │ PUT  /api/employees/{id}           │  ✅   │  ❌  │
 * │ DELETE /api/employees/{id}         │  ✅   │  ❌  │
 * │ GET  /api/employees/analytics/**   │  ✅   │  ✅  │
 * │ GET  /api/auth/me                  │  ✅   │  ✅  │
 * └────────────────────────────────────┴────────┴──────┘
 * </pre>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ── Custom JSON entry point for API endpoints ──────────────────────────────

    /**
     * Returns a structured {@code 401 application/json} response instead of
     * redirecting to the HTML login page when an unauthenticated request hits
     * any {@code /api/**} endpoint.
     *
     * <p>Without this, Spring Security sends {@code 302 → /login.html → 200 HTML},
     * and {@code fetch().then(r => r.json())} throws a parse error because it
     * receives HTML, not JSON. The dashboard Fetch calls in {@code app.js} then
     * detect {@code res.status === 401} and cleanly redirect to login.
     */
    @Bean
    public AuthenticationEntryPoint apiAuthEntryPoint() {
        return (HttpServletRequest request,
                HttpServletResponse response,
                AuthenticationException authException) -> {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", LocalDateTime.now().toString());
            body.put("status",    401);
            body.put("error",     "Unauthorized");
            body.put("message",   "Authentication required. Please sign in.");
            body.put("path",      request.getRequestURI());

            new ObjectMapper().writeValue(response.getWriter(), body);
        };
    }

    // ── Security Filter Chain ──────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Authorization rules (order matters — most specific first) ────
            .authorizeHttpRequests(auth -> auth

                // Static assets — always public (including dashboard.html for
                // the redirect after successful login)
                .requestMatchers(
                    "/", "/login.html", "/dashboard.html",
                    "/js/**", "/css/**", "/images/**",
                    "/*.ico", "/*.png", "/*.svg"
                ).permitAll()

                // Swagger / OpenAPI — permitted in development
                .requestMatchers(
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/v3/api-docs/**", "/webjars/**"
                ).permitAll()

                // Analytics — both roles
                .requestMatchers(HttpMethod.GET, "/api/employees/analytics/**")
                    .hasAnyRole("ADMIN", "USER")

                // Read employees — both roles
                .requestMatchers(HttpMethod.GET, "/api/employees/**")
                    .hasAnyRole("ADMIN", "USER")

                // Write — ADMIN only
                .requestMatchers(HttpMethod.POST,   "/api/employees/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/employees/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("ADMIN")

                // Auth info — any authenticated user
                .requestMatchers("/api/auth/**").authenticated()

                // Everything else requires auth
                .anyRequest().authenticated()
            )

            // ── Form login ───────────────────────────────────────────────────
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard.html", true)
                .failureUrl("/login.html?error=true")
                .permitAll()
            )

            // ── Logout ───────────────────────────────────────────────────────
            // With CSRF disabled, GET /logout is accepted by LogoutFilter.
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ── Custom entry point for /api/** ───────────────────────────────
            // Unauthenticated calls to /api/** get 401 JSON instead of
            // 302 → HTML redirect. This is what breaks the Fetch handshake.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    apiAuthEntryPoint(),
                    request -> request.getRequestURI().startsWith("/api/")
                )
            )

            // ── CSRF ─────────────────────────────────────────────────────────
            // Disabled for dev. Re-enable with CookieCsrfTokenRepository in prod.
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    // ── In-Memory Users ───────────────────────────────────────────────────────

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails regularUser = User.builder()
                .username("user")
                .password(passwordEncoder.encode("user123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, regularUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
