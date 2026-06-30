package com.shuttlematch.infrastructure.config;

import com.shuttlematch.domain.service.MatchingDomainService;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ドメインサービス等を Spring の Bean として公開する設定。
 * ドメイン層を Spring に依存させないため、Bean 登録はインフラ層で行う。
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public MatchingDomainService matchingDomainService() {
        return new MatchingDomainService();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
