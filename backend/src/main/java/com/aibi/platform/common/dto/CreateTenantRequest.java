package com.aibi.platform.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank(message = "租户编码不能为空")
        @Size(min = 2, max = 64, message = "租户编码长度必须为 2 到 64 个字符")
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9_-]*[a-z0-9])?$",
                message = "租户编码只能包含小写字母、数字、下划线和连字符，且必须以字母或数字开头和结尾")
        String tenantCode,

        @NotBlank(message = "租户名称不能为空")
        @Size(max = 200, message = "租户名称长度不能超过 200 个字符")
        String tenantName,

        @Size(max = 128, message = "时区长度不能超过 128 个字符")
        String timezone,

        @Pattern(regexp = "^[A-Z]{3}$", message = "默认币种必须是 3 位大写字母")
        String defaultCurrency) {
}
