package com.sajith.payments.redesign.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Completely bypasses Spring Security for H2 console — active only when
 * spring.h2.console.enabled=true (local profile only).
 *
 * Why WebSecurityCustomizer.ignoring() instead of a SecurityFilterChain:
 *   A SecurityFilterChain still runs the security filter pipeline, which adds
 *   response headers (X-Frame-Options: DENY by default). H2 console uses HTML
 *   framesets — any X-Frame-Options header causes the browser to block the
 *   inner frames showing "localhost refused to connect".
 *
 *   ignoring() removes the path from Spring Security entirely:
 *     - No filter chain runs
 *     - No security headers added (so X-Frame-Options is never set)
 *     - No CSRF, no auth, no session management
 *
 * Copy to office code: yes, update package name only.
 * Do NOT copy SecurityConfig.java — office code already has one.
 */
@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class H2ConsoleSecurityConfig {

    @Bean
    public WebSecurityCustomizer h2ConsoleWebSecurityCustomizer() {
        return (WebSecurity web) -> web.ignoring()
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**"));
    }
}
