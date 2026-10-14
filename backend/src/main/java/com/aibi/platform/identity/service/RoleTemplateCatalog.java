package com.aibi.platform.identity.service;

import java.util.HashSet;
import java.util.Set;

/** 默认资源角色不附带IAM管理能力，身份管理权限必须单独委派。 */
public final class RoleTemplateCatalog {

    private RoleTemplateCatalog() {
    }

    public static Set<String> permissions(String code) {
        if ("admin".equals(code)) {
            Set<String> actions = new HashSet<>(Set.of("dataset:read", "dataset:manage", "dataset:publish",
                    "metric:read", "metric:manage", "query:execute", "chart:manage", "dashboard:read",
                    "dashboard:manage", "report:export", "ai:ask", "quality:read", "quality:manage"));
            ResourceActionCatalog.DEFINITIONS.forEach(value -> actions.add(value.code()));
            return Set.copyOf(actions);
        }
        if ("analyst".equals(code)) {
            return Set.of("datasource:read", "file:upload", "file:read", "file:download",
                    "import:create", "import:read", "import:run", "import:cancel",
                    "dataset:read", "dataset:manage", "dataset:publish", "metric:read", "metric:manage",
                    "query:execute", "chart:manage", "dashboard:read", "dashboard:manage", "report:export", "ai:ask", "quality:read");
        }
        if ("viewer".equals(code)) {
            return Set.of("dataset:read", "metric:read", "query:execute", "dashboard:read");
        }
        return Set.of();
    }
}
