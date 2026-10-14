package com.aibi.platform.analytics.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowPlan;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowRequest;
import com.aibi.platform.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/analytics", "/api/v1/analytics"})
@ConditionalOnProperty(name = "app.analytics.enabled", havingValue = "true")
@Tag(name = "P2 高级分析", description = "异常检测、训练窗口、预测回测和预测区间")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @PostMapping("/anomalies/detect")
    @Operation(summary = "按业务阈值和滚动统计带检测异常")
    public AnomalyResult detect(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody AnomalyRequest request) {
        return service.detect(authorization, tenantId, request);
    }

    @PostMapping("/forecast/windows")
    @Operation(summary = "生成无未来数据泄漏的滚动训练和验证窗口")
    public WindowPlan windows(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody WindowRequest request) {
        return service.windows(authorization, tenantId, request);
    }

    @PostMapping("/forecast")
    @Operation(summary = "执行回测并返回点预测和预测区间")
    public ForecastResult forecast(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody ForecastRequest request) {
        return service.forecast(authorization, tenantId, request);
    }
}
