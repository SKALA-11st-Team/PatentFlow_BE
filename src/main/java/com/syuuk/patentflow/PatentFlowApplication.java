package com.syuuk.patentflow;

import com.syuuk.patentflow.mailing.config.MailOAuth2Properties;
import com.syuuk.patentflow.patent.config.PatentLookupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // QuarterActivationScheduler의 @Scheduled 크론이 동작하려면 반드시 필요
@EnableConfigurationProperties({PatentLookupProperties.class, MailOAuth2Properties.class})
public class PatentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatentFlowApplication.class, args);
    }
}
