package com.aibi.platform.source;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.ResourceAccessManager;
import java.time.Instant;

/** 连接器/版本组件测试的显式已授权夹具；真实鉴权由API安全矩阵测试。 */
public final class DatasourceTestAccess {
    public static final String AUTH = "Bearer authorized-component-fixture";

    private DatasourceTestAccess() {
    }

    public static ResourceAccessManager authorized() {
        var access = mock(ResourceAccessManager.class);
        when(access.requireWorkspace(anyString(), anyLong(), anyLong(), anyString()))
                .thenAnswer(call -> claims(call.getArgument(1)));
        when(access.requireTenant(anyString(), anyLong())).thenAnswer(call -> claims(call.getArgument(1)));
        when(access.can(any(), anyLong(), anyString(), anyLong(), anyString())).thenReturn(true);
        return access;
    }

    private static TokenService.TenantClaims claims(long tenantId) {
        return new TokenService.TenantClaims(1, 1, tenantId, 8001, 1, 1, Instant.now().plusSeconds(60));
    }
}
