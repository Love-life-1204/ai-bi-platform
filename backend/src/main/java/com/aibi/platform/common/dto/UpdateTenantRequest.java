package com.aibi.platform.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank(message = "租户名称不能为空")
        @Size(max = 200, message = "租户名称长度不能超过 200 个字符")
        String tenantName,

        @NotBlank(message = "时区不能为空")
        @Size(max = 128, message = "时区长度不能超过 128 个字符")
        String timezone,

        @NotBlank(message = "默认币种不能为空")
        @Pattern(regexp = "^[A-Z]{3}$", message = "默认币种必须是 3 位大写字母")
        String defaultCurrency,

        @PositiveOrZero(message = "锁版本不能为负数")
        long lockVersion) {
}
