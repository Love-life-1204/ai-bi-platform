package com.aibi.platform.controller;

import com.aibi.platform.common.dto.SystemStatusData;
import com.aibi.platform.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/system", "/api/v1/system"})
@Tag(name = "系统", description = "系统基础状态接口")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    @Operation(summary = "查询服务状态", description = "不访问外部数据库，用于基础存活检查。")
    public SystemStatusData getStatus() {
        return systemStatusService.getCurrentStatus();
    }
}
