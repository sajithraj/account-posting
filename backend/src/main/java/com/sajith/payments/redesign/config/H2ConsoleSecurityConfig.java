package com.sajith.payments.redesign.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.h2.H2ConsoleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Unlocks the H2 console only when spring.h2.console.enabled=true (local profile).
 *
 * Why this is needed even though the main SecurityConfig already has permitAll():
 *   - Spring Security adds X-Frame-Options: DENY by default on every response
 *   - H2 console renders inside an iframe — browser refuses to load it
 *   - This chain disables that header ONLY for /h2-console/** requests
 *
 * @Order(1) ensures this chain intercepts /h2-console/** before the main chain.
 * securityMatcher scopes it strictly to /h2-console/** — all other URLs
 * continue to be handled by the existing SecurityConfig unchanged.
 *
 * When moving to office code: copy this file only, update the package name.
 * Do NOT copy SecurityConfig.java — office code already has one.
 */
@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class H2ConsoleSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http,
                                                             H2ConsoleProperties h2ConsoleProperties) throws Exception {
        String consolePath = h2ConsoleProperties.getPath() + "/**";

        http
            .securityMatcher(consolePath)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
