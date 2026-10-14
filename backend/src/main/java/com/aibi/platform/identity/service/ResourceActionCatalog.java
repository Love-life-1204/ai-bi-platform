package com.aibi.platform.identity.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 新动作与旧数据源管理权限的兼容映射；未知动作保持默认拒绝。 */
public final class ResourceActionCatalog {

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition(100, "datasource:read", "读取数据源", "DATASOURCE", "read"),
            new Definition(101, "datasource:create", "创建数据源", "DATASOURCE", "create"),
            new Definition(102, "datasource:update", "修改数据源", "DATASOURCE", "update"),
            new Definition(103, "datasource:delete", "删除数据源", "DATASOURCE", "delete"),
            new Definition(104, "datasource:test", "测试数据源", "DATASOURCE", "test"),
            new Definition(105, "file:upload", "上传文件", "FILE", "upload"),
            new Definition(106, "file:read", "预览文件", "FILE", "read"),
            new Definition(107, "file:download", "下载文件", "FILE", "download"),
            new Definition(108, "import:create", "创建导入", "IMPORT", "create"),
            new Definition(21, "import:read", "查看文件导入", "IMPORT", "read"),
            new Definition(109, "import:run", "执行导入", "IMPORT", "run"),
            new Definition(110, "import:cancel", "取消导入", "IMPORT", "cancel"),
            new Definition(111, "sync:create", "创建同步", "SYNC", "create"),
            new Definition(23, "sync:read", "查看数据同步", "SYNC", "read"),
            new Definition(112, "sync:update", "修改同步", "SYNC", "update"),
            new Definition(113, "sync:run", "运行同步", "SYNC", "run"),
            new Definition(114, "sync:cancel", "取消同步", "SYNC", "cancel"),
            new Definition(115, "sync:compensate", "补偿同步", "SYNC", "compensate"));
    private static final Set<String> CODES = DEFINITIONS.stream().map(Definition::code).collect(Collectors.toSet());

    private ResourceActionCatalog() {
    }

    public static String legacyAlias(String code) {
        if (!CODES.contains(code)) {
            return null;
        }
        if (code.startsWith("datasource:")) {
            return "datasource:manage";
        }
        if (code.startsWith("import:")) {
            return "import:manage";
        }
        return code.startsWith("sync:") ? "sync:manage" : null;
    }

    public record Definition(long id, String code, String name, String resourceType, String action) {
    }
}
