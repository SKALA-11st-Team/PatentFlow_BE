package com.syuuk.patentflow.settings.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * SETTINGS-09: 검토 기간 템플릿 기본 시드를 SettingsService 생성자(트랜잭션 밖 쓰기)에서 분리한다.
 * @PostConstruct에서 SettingsService의 @Transactional 메서드를 호출(다른 빈 경유라 프록시 적용 → 트랜잭션 보장).
 * QuarterActivationScheduler가 @DependsOn으로 이 빈을 선행시켜 시작 시점 템플릿 의존을 만족한다.
 */
@Component
public class ReviewPeriodTemplateSeeder {

    private final SettingsService settingsService;

    public ReviewPeriodTemplateSeeder(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostConstruct
    public void seed() {
        settingsService.seedDefaultTemplatesIfNeeded();
    }
}
