package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.util.List;

public record DepartmentData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        @JsonResourceId Long parentId,
        String departmentCode,
        String departmentName,
        int sortOrder,
        long lockVersion,
        List<DepartmentData> children) {
}
