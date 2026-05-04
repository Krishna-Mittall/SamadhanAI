package com.samadhanai.samadhanai.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ✅ Public — no login needed
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/dashboard/**").permitAll()

                        // ✅ AI photo analyze — public (used before submit)
                        .requestMatchers(HttpMethod.POST, "/api/complaints/analyze").permitAll()

                        // ✅ Track complaint — public (GET only)
                        .requestMatchers(HttpMethod.GET, "/api/complaints/*").permitAll()

                        // ✅ Static frontend files
                        .requestMatchers(
                                "/", "/**.html", "/css/**", "/js/**",
                                "/uploads/**", "/favicon.ico", "/favicon/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                        ).permitAll()

                        // 🔐 Submit complaint — logged in users
                        .requestMatchers(HttpMethod.POST, "/api/complaints").hasAnyRole("USER", "ADMIN")

                        // 🔐 Edit + photos + send — logged in users
                        .requestMatchers(
                                "/api/complaints/*/edit",
                                "/api/complaints/*/photos",
                                "/api/complaints/*/send"
                        ).hasAnyRole("USER", "ADMIN")

                        // ✅ NEW: Way 2 — Citizen resolves own complaint
                        // PUT /api/complaints/{ref}/resolve
                        .requestMatchers(
                                HttpMethod.PUT, "/api/complaints/*/resolve"
                        ).hasAnyRole("USER", "ADMIN")

                        // 🔐 Status update — Admin only
                        .requestMatchers(
                                HttpMethod.PUT, "/api/complaints/*/status"
                        ).hasRole("ADMIN")

                        // 🔐 User profile — logged in users
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")

                        // 🔐 Admin panel — Admin only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything else → authenticated
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // ✅ Return JSON on 401/403 — not empty body
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    new ObjectMapper().writeValueAsString(
                                            java.util.Map.of(
                                                    "success",   false,
                                                    "message",   "Session expired. Please login again.",
                                                    "timestamp", java.time.LocalDateTime.now().toString()
                                            )
                                    )
                            );
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    new ObjectMapper().writeValueAsString(
                                            java.util.Map.of(
                                                    "success",   false,
                                                    "message",   "Access denied. Please login again.",
                                                    "timestamp", java.time.LocalDateTime.now().toString()
                                            )
                                    )
                            );
                        })
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}