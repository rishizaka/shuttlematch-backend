package com.shuttlematch.infrastructure.config;

import com.shuttlematch.domain.service.MatchingDomainService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインサービスを Spring の Bean として公開する設定。
 * ドメイン層を Spring に依存させないため、Bean 登録はインフラ層で行う。
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public MatchingDomainService matchingDomainService() {
        return new MatchingDomainService();
    }
}
