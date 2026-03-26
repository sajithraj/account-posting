package com.sajith.payments.redesign.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class H2ConsoleSecurityConfig {

    @Bean
    public WebSecurityCustomizer h2ConsoleWebSecurityCustomizer() {
        return (WebSecurity web) -> web.ignoring()
                .requestMatchers("/h2-console/**");
    }
}
