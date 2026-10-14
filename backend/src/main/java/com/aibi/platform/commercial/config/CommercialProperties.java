package com.aibi.platform.commercial.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.commercial")
public class CommercialProperties {

    private String paymentWebhookSecret = "local-payment-secret-change-me";
    private long paymentClockSkewSeconds = 300;
    private int exportWorkers = 2;
    private int exportQueueCapacity = 20;
    private long exportRetentionSeconds = 3600;
    private long alertSuppressionSeconds = 900;
    private long supportGrantMaxSeconds = 7200;
    private String platformOperationsToken = "local-platform-operations-change-me";
    private String internalServiceToken = "local-commercial-internal-change-me";
    private boolean quotaEnforcementEnabled;
    private List<OidcSecret> oidcSecrets = new ArrayList<>();
    private boolean emailNotificationsEnabled;
    private String notificationFrom = "";

    public String getPaymentWebhookSecret() {
        return paymentWebhookSecret;
    }

    public void setPaymentWebhookSecret(String paymentWebhookSecret) {
        this.paymentWebhookSecret = paymentWebhookSecret;
    }

    public long getPaymentClockSkewSeconds() {
        return paymentClockSkewSeconds;
    }

    public void setPaymentClockSkewSeconds(long paymentClockSkewSeconds) {
        this.paymentClockSkewSeconds = paymentClockSkewSeconds;
    }

    public int getExportWorkers() {
        return exportWorkers;
    }

    public void setExportWorkers(int exportWorkers) {
        this.exportWorkers = exportWorkers;
    }

    public int getExportQueueCapacity() {
        return exportQueueCapacity;
    }

    public void setExportQueueCapacity(int exportQueueCapacity) {
        this.exportQueueCapacity = exportQueueCapacity;
    }

    public long getExportRetentionSeconds() {
        return exportRetentionSeconds;
    }

    public void setExportRetentionSeconds(long exportRetentionSeconds) {
        this.exportRetentionSeconds = exportRetentionSeconds;
    }

    public long getAlertSuppressionSeconds() {
        return alertSuppressionSeconds;
    }

    public void setAlertSuppressionSeconds(long alertSuppressionSeconds) {
        this.alertSuppressionSeconds = alertSuppressionSeconds;
    }

    public long getSupportGrantMaxSeconds() {
        return supportGrantMaxSeconds;
    }

    public void setSupportGrantMaxSeconds(long supportGrantMaxSeconds) {
        this.supportGrantMaxSeconds = supportGrantMaxSeconds;
    }

    public String getPlatformOperationsToken() {
        return platformOperationsToken;
    }

    public void setPlatformOperationsToken(String platformOperationsToken) {
        this.platformOperationsToken = platformOperationsToken;
    }

    public String getInternalServiceToken() {
        return internalServiceToken;
    }

    public void setInternalServiceToken(String internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }

    public boolean isQuotaEnforcementEnabled() {
        return quotaEnforcementEnabled;
    }

    public void setQuotaEnforcementEnabled(boolean quotaEnforcementEnabled) {
        this.quotaEnforcementEnabled = quotaEnforcementEnabled;
    }

    public List<OidcSecret> getOidcSecrets() {
        return oidcSecrets;
    }

    public void setOidcSecrets(List<OidcSecret> oidcSecrets) {
        this.oidcSecrets = oidcSecrets;
    }

    public String oidcSecret(String reference) {
        return oidcSecrets.stream().filter(value -> value.getRef().equals(reference))
                .map(OidcSecret::getValue).filter(value -> !value.isBlank()).findFirst()
                .orElseThrow(() -> new IllegalStateException("OIDC 客户端密钥引用不存在"));
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public String getNotificationFrom() {
        return notificationFrom;
    }

    public void setNotificationFrom(String notificationFrom) {
        this.notificationFrom = notificationFrom;
    }

    public static class OidcSecret {

        private String ref = "";
        private String value = "";

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
