package com.aibi.platform.service.impl;

import com.aibi.platform.common.dto.SystemStatusData;
import com.aibi.platform.entity.SystemStatusEntity;
import com.aibi.platform.manager.SystemStatusManager;
import com.aibi.platform.service.SystemStatusService;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {

    private final SystemStatusManager systemStatusManager;

    public SystemStatusServiceImpl(SystemStatusManager systemStatusManager) {
        this.systemStatusManager = systemStatusManager;
    }

    @Override
    public SystemStatusData getCurrentStatus() {
        SystemStatusEntity entity = systemStatusManager.loadCurrentStatus();
        return new SystemStatusData(entity.serviceName(), entity.status(), entity.checkedAt());
    }
}
