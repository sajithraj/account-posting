package com.sajith.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@EnableAsync
public class AccountPostingApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountPostingApplication.class, args);
    }
}
