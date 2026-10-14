package com.aibi.platform.analytics.service;

import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowPlan;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowRequest;
import com.aibi.platform.analytics.manager.TimeSeriesAnalyticsManager;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final TimeSeriesAnalyticsManager manager;
    private final SemanticMapper semanticMapper;
    private final SemanticAccessManager access;

    public AnalyticsService(TimeSeriesAnalyticsManager manager, SemanticMapper semanticMapper,
            SemanticAccessManager access) {
        this.manager = manager;
        this.semanticMapper = semanticMapper;
        this.access = access;
    }

    public AnomalyResult detect(String authorization, long tenantId, AnomalyRequest request) {
        authorize(authorization, tenantId, request.metricVersionId());
        return manager.detect(request);
    }

    public WindowPlan windows(String authorization, long tenantId, WindowRequest request) {
        authorize(authorization, tenantId, request.metricVersionId());
        return manager.windows(request);
    }

    public ForecastResult forecast(String authorization, long tenantId, ForecastRequest request) {
        authorize(authorization, tenantId, request.metricVersionId());
        return manager.forecast(request);
    }

    private void authorize(String authorization, long tenantId, long metricVersionId) {
        MetricVersion version = semanticMapper.findMetricVersion(tenantId, metricVersionId)
                .orElseThrow(() -> new NotFoundException("指标版本不存在"));
        if (!"PUBLISHED".equals(version.status())) {
            throw new ConflictException("高级分析只能使用已发布指标版本");
        }
        Metric metric = semanticMapper.findMetric(tenantId, version.metricId())
                .orElseThrow(() -> new NotFoundException("指标不存在"));
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(),
                "metric:read", "read");
    }
}
