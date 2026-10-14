package com.aibi.platform.commercial.service;

import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.identity.mapper.MemberMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final CommercialProperties properties;
    private final MemberMapper members;
    private final UserMapper users;
    private final JavaMailSender mailSender;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "commercial-email-worker");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationDeliveryService(CommercialProperties properties, MemberMapper members,
            UserMapper users, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.members = members;
        this.users = users;
        this.mailSender = mailSenderProvider.getIfAvailable();
        if (properties.isEmailNotificationsEnabled()
                && (mailSender == null || properties.getNotificationFrom().isBlank())) {
            throw new IllegalStateException("邮件通知已启用但 SMTP 或发件人配置不完整");
        }
    }

    public void deliver(long tenantId, long recipientMemberId, String subject, String content) {
        if (!properties.isEmailNotificationsEnabled()) {
            return;
        }
        String email = members.findById(tenantId, recipientMemberId)
                .filter(value -> "ACTIVE".equals(value.memberStatus()))
                .flatMap(value -> users.findById(value.userId()))
                .filter(value -> "ACTIVE".equals(value.userStatus()))
                .map(value -> value.email()).orElse(null);
        if (email == null) {
            return;
        }
        executor.execute(() -> send(email, subject, content));
    }

    private void send(String email, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getNotificationFrom());
            message.setTo(email);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("商业化邮件发送失败: recipient={}", email, exception);
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
