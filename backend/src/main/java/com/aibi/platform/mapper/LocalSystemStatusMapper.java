package com.aibi.platform.mapper;

import com.aibi.platform.entity.SystemStatusEntity;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class LocalSystemStatusMapper implements SystemStatusMapper {

    private final String serviceName;

    public LocalSystemStatusMapper(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public SystemStatusEntity selectCurrentStatus() {
        return new SystemStatusEntity(serviceName, "UP", Instant.now());
    }
}
