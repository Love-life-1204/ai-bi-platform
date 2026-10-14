package com.aibi.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.controller.SystemStatusController;
import com.aibi.platform.controller.TenantController;
import com.aibi.platform.entity.SystemStatusEntity;
import com.aibi.platform.entity.TenantEntity;
import com.aibi.platform.manager.SystemStatusManager;
import com.aibi.platform.manager.TenantManager;
import com.aibi.platform.mapper.SystemStatusMapper;
import com.aibi.platform.mapper.TenantMapper;
import com.aibi.platform.service.SystemStatusService;
import com.aibi.platform.service.TenantService;
import com.aibi.platform.service.impl.SystemStatusServiceImpl;
import com.aibi.platform.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.Test;

class LayeringTest {

    @Test
    void systemStatusUseCaseShouldCoverAllRequiredLayers() {
        assertThat(SystemStatusController.class.getPackageName()).endsWith(".controller");
        assertThat(SystemStatusService.class.getPackageName()).endsWith(".service");
        assertThat(SystemStatusServiceImpl.class.getPackageName()).endsWith(".service.impl");
        assertThat(SystemStatusManager.class.getPackageName()).endsWith(".manager");
        assertThat(SystemStatusMapper.class.getPackageName()).endsWith(".mapper");
        assertThat(SystemStatusEntity.class.getPackageName()).endsWith(".entity");
    }

    @Test
    void tenantCreateUseCaseShouldCoverAllRequiredLayers() {
        assertThat(TenantController.class.getPackageName()).endsWith(".controller");
        assertThat(TenantService.class.getPackageName()).endsWith(".service");
        assertThat(TenantServiceImpl.class.getPackageName()).endsWith(".service.impl");
        assertThat(TenantManager.class.getPackageName()).endsWith(".manager");
        assertThat(TenantMapper.class.getPackageName()).endsWith(".mapper");
        assertThat(TenantEntity.class.getPackageName()).endsWith(".entity");
    }
}
