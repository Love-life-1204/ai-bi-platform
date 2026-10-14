package com.aibi.platform.manager;

import com.aibi.platform.entity.SystemStatusEntity;
import com.aibi.platform.mapper.SystemStatusMapper;
import org.springframework.stereotype.Component;

@Component
public class SystemStatusManager {

    private final SystemStatusMapper systemStatusMapper;

    public SystemStatusManager(SystemStatusMapper systemStatusMapper) {
        this.systemStatusMapper = systemStatusMapper;
    }

    public SystemStatusEntity loadCurrentStatus() {
        return systemStatusMapper.selectCurrentStatus();
    }
}
