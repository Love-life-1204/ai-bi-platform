package com.aibi.platform.commercial.config;

import com.aibi.platform.commercial.service.CommercialService;
import java.time.Instant;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class CommercialSchedulingConfiguration {

    private final CommercialService service;

    public CommercialSchedulingConfiguration(CommercialService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.commercial.subscription-scan-delay-ms:60000}")
    public void deliverScheduledReports() {
        service.runDueSubscriptions(Instant.now());
    }
}
