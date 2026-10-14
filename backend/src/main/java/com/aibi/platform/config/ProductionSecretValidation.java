package com.aibi.platform.config;

import com.aibi.platform.ai.config.AiProperties;
import com.aibi.platform.commercial.config.CommercialProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 生产启动时拒绝源码公开的开发密钥，异常信息只包含配置名称。 */
@Component
@Profile("prod")
public class ProductionSecretValidation {

    private final AiProperties ai;
    private final CommercialProperties commercial;

    public ProductionSecretValidation(AiProperties ai, CommercialProperties commercial) {
        this.ai = ai;
        this.commercial = commercial;
    }

    @PostConstruct
    public void validate() {
        requireSecret("AI_SHARED_SECRET", ai.getSharedSecret());
        requireSecret("PAYMENT_WEBHOOK_SECRET", commercial.getPaymentWebhookSecret());
        requireSecret("PLATFORM_OPERATIONS_TOKEN", commercial.getPlatformOperationsToken());
        requireSecret("COMMERCIAL_INTERNAL_TOKEN", commercial.getInternalServiceToken());
    }

    private void requireSecret(String name, String value) {
        if (value == null || value.strip().length() < 32 || value.startsWith("local-")) {
            throw new IllegalStateException(name + " 必须显式配置为至少 32 字符的非默认生产密钥");
        }
    }
}
