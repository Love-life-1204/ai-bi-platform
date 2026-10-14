package com.aibi.platform.commercial.service;

import com.aibi.platform.commercial.config.CommercialProperties;
import com.aibi.platform.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class CommercialAccessService {

    private final CommercialProperties properties;

    public CommercialAccessService(CommercialProperties properties) {
        this.properties = properties;
    }

    public void requirePlatform(String token) {
        require(token, properties.getPlatformOperationsToken());
    }

    public void requireInternal(String token) {
        require(token, properties.getInternalServiceToken());
    }

    private void require(String supplied, String expected) {
        if (supplied == null || expected == null || expected.isBlank()
                || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                        expected.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException();
        }
    }
}
