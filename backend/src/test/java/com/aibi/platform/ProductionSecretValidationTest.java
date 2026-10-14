package com.aibi.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aibi.platform.ai.config.AiProperties;
import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.config.ProductionSecretValidation;
import org.junit.jupiter.api.Test;

class ProductionSecretValidationTest {

    @Test
    void shouldRejectDevelopmentDefaults() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecretValidation(
                new AiProperties(), new CommercialProperties()).validate());
    }

    @Test
    void shouldValidateEveryInternalSecretWithoutLoggingValues() {
        AiProperties ai = new AiProperties();
        CommercialProperties commercial = new CommercialProperties();
        String testSecret = "qa-only-explicit-secret-for-validation-2026";
        ai.setSharedSecret(testSecret);
        commercial.setPaymentWebhookSecret(testSecret);
        commercial.setPlatformOperationsToken(testSecret);
        commercial.setInternalServiceToken(testSecret);
        ProductionSecretValidation validation = new ProductionSecretValidation(ai, commercial);
        assertDoesNotThrow(validation::validate);
        commercial.setInternalServiceToken("short-sensitive-test-value");
        IllegalStateException error = assertThrows(IllegalStateException.class, validation::validate);
        assertFalse(error.getMessage().contains("short-sensitive-test-value"));
        commercial.setInternalServiceToken(testSecret);
        commercial.setPlatformOperationsToken("local-platform-operations-change-me");
        assertThrows(IllegalStateException.class, validation::validate);
        commercial.setPlatformOperationsToken(testSecret);
        commercial.setPaymentWebhookSecret("local-payment-secret-change-me");
        assertThrows(IllegalStateException.class, validation::validate);
    }
}
