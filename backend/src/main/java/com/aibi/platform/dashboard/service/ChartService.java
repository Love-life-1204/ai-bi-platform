package com.aibi.platform.dashboard.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.dashboard.dto.DashboardDtos.ChartData;
import com.aibi.platform.dashboard.dto.DashboardDtos.ChartValidationData;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateChartRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.UpdateChartRequest;
import com.aibi.platform.dashboard.manager.ChartSpecValidator;
import com.aibi.platform.dashboard.mapper.DashboardMapper;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.query.service.QueryService;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class ChartService {

    private final DashboardMapper mapper;
    private final SemanticMapper semanticMapper;
    private final SemanticAccessManager access;
    private final ChartSpecValidator validator;
    private final QueryService queryService;

    public ChartService(DashboardMapper mapper, SemanticMapper semanticMapper,
            SemanticAccessManager access, ChartSpecValidator validator, QueryService queryService) {
        this.mapper = mapper;
        this.semanticMapper = semanticMapper;
        this.access = access;
        this.validator = validator;
        this.queryService = queryService;
    }

    @MySqlTransactional(timeout = 5)
    public ChartData create(String authorization, long tenantId, CreateChartRequest request) {
        TokenService.TenantClaims claims = access.requireWorkspace(
                authorization, tenantId, request.workspaceId());
        access.requirePermission(authorization, tenantId, request.workspaceId(),
                "chart:manage", "manage");
        requirePublishedDataset(tenantId, request.datasetVersionId());
        requireValid(request.chartSpec(), request.queryPlan(), request.datasetVersionId());
        Instant now = Instant.now();
        Chart chart = mapper.insertChart(new Chart(newId(), tenantId, request.workspaceId(),
                request.code(), request.name().trim(), claims.memberId(), null, "DRAFT",
                now, now, 0));
        ChartVersion draft = mapper.insertChartVersion(new ChartVersion(
                newId(), tenantId, chart.id(), 1, request.datasetVersionId(),
                request.queryPlan(), request.chartSpec().deepCopy(), "DRAFT", null, 0));
        return new ChartData(chart, null, draft);
    }

    @MySqlTransactional(timeout = 5)
    public ChartData update(String authorization, long tenantId, long chartId,
            UpdateChartRequest request) {
        Chart chart = requiredChart(tenantId, chartId);
        requireManage(authorization, chart);
        requirePublishedDataset(tenantId, request.datasetVersionId());
        requireValid(request.chartSpec(), request.queryPlan(), request.datasetVersionId());
        Chart updated = mapper.updateChart(new Chart(chart.id(), chart.tenantId(),
                chart.workspaceId(), chart.code(), request.name().trim(), chart.ownerMemberId(),
                chart.currentVersionId(), "DRAFT", chart.createdAt(), Instant.now(),
                chart.lockVersion() + 1), request.lockVersion());
        ChartVersion draft = draft(tenantId, chartId);
        if (draft == null) {
            int next = mapper.listChartVersions(tenantId, chartId).stream()
                    .mapToInt(ChartVersion::versionNo).max().orElse(0) + 1;
            draft = mapper.insertChartVersion(new ChartVersion(newId(), tenantId, chartId,
                    next, request.datasetVersionId(), request.queryPlan(),
                    request.chartSpec().deepCopy(), "DRAFT", null, 0));
        } else {
            draft = mapper.updateChartVersion(new ChartVersion(draft.id(), tenantId, chartId,
                    draft.versionNo(), request.datasetVersionId(), request.queryPlan(),
                    request.chartSpec().deepCopy(), "DRAFT", null, draft.lockVersion() + 1),
                    draft.lockVersion());
        }
        return new ChartData(updated, current(tenantId, updated), draft);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<Chart> list(String authorization, long tenantId, Long workspaceId) {
        if (workspaceId != null) {
            access.requireWorkspace(authorization, tenantId, workspaceId);
            access.requirePermission(authorization, tenantId, workspaceId,
                    "dashboard:read", "read");
            return mapper.listCharts(tenantId, workspaceId);
        }
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return mapper.listCharts(tenantId, null).stream()
                .filter(value -> access.canAccessWorkspace(
                        tenantId, value.workspaceId(), claims.memberId()))
                .filter(value -> access.hasPermission(authorization, tenantId,
                        value.workspaceId(), "dashboard:read", "read")).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public ChartData get(String authorization, long tenantId, long chartId) {
        Chart chart = requiredChart(tenantId, chartId);
        requireRead(authorization, chart);
        ChartVersion visibleDraft = access.hasPermission(authorization, tenantId,
                chart.workspaceId(), "chart:manage", "manage")
                ? draft(tenantId, chartId) : null;
        return new ChartData(chart, current(tenantId, chart), visibleDraft);
    }

    @MySqlTransactional(timeout = 5)
    public ChartValidationData validate(String authorization, long tenantId, long chartId) {
        Chart chart = requiredChart(tenantId, chartId);
        requireManage(authorization, chart);
        ChartVersion draft = requiredDraft(tenantId, chartId);
        ChartValidationData result = validator.validate(
                draft.chartSpec(), draft.queryPlan(), draft.datasetVersionId());
        if (result.valid()) {
            queryService.validate(authorization, tenantId, draft.queryPlan());
        }
        return result;
    }

    @MySqlTransactional(timeout = 5)
    public ChartVersion publish(String authorization, long tenantId, long chartId) {
        Chart chart = requiredChart(tenantId, chartId);
        requireManage(authorization, chart);
        ChartVersion draft = requiredDraft(tenantId, chartId);
        requireValid(draft.chartSpec(), draft.queryPlan(), draft.datasetVersionId());
        queryService.validate(authorization, tenantId, draft.queryPlan());
        ChartVersion published = mapper.updateChartVersion(new ChartVersion(
                draft.id(), draft.tenantId(), draft.chartId(), draft.versionNo(),
                draft.datasetVersionId(), draft.queryPlan(), draft.chartSpec(),
                "PUBLISHED", Instant.now(), draft.lockVersion() + 1), draft.lockVersion());
        mapper.updateChart(new Chart(chart.id(), chart.tenantId(), chart.workspaceId(),
                chart.code(), chart.name(), chart.ownerMemberId(), published.id(), "PUBLISHED",
                chart.createdAt(), Instant.now(), chart.lockVersion() + 1), chart.lockVersion());
        return published;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<ChartVersion> versions(String authorization, long tenantId, long chartId) {
        Chart chart = requiredChart(tenantId, chartId);
        requireRead(authorization, chart);
        boolean canManage = access.hasPermission(authorization, tenantId,
                chart.workspaceId(), "chart:manage", "manage");
        return mapper.listChartVersions(tenantId, chartId).stream()
                .filter(value -> canManage || "PUBLISHED".equals(value.status())).toList();
    }

    public Chart requiredChart(long tenantId, long chartId) {
        return mapper.findChart(tenantId, chartId)
                .orElseThrow(() -> new NotFoundException("图表不存在"));
    }

    public ChartVersion requiredVersion(long tenantId, long versionId) {
        return mapper.findChartVersion(tenantId, versionId)
                .orElseThrow(() -> new NotFoundException("图表版本不存在"));
    }

    private ChartVersion current(long tenantId, Chart chart) {
        if (chart.currentVersionId() == null) {
            return null;
        }
        return mapper.findChartVersion(tenantId, chart.currentVersionId())
                .orElseThrow(() -> new NotFoundException("当前图表版本不存在"));
    }

    private void requirePublishedDataset(long tenantId, long datasetVersionId) {
        var version = semanticMapper.findDatasetVersion(tenantId, datasetVersionId)
                .orElseThrow(() -> new NotFoundException("数据集版本不存在"));
        if (!"PUBLISHED".equals(version.status())) {
            throw new ConflictException("图表只能引用已发布的数据集版本");
        }
    }

    private void requireValid(com.fasterxml.jackson.databind.JsonNode spec,
            com.aibi.platform.query.dto.QueryDtos.QueryPlan plan, long datasetVersionId) {
        ChartValidationData result = validator.validate(spec, plan, datasetVersionId);
        if (!result.valid()) {
            throw new ApiException(ApiErrorCode.INVALID_ARGUMENT, "图表配置校验未通过", result);
        }
    }

    private ChartVersion requiredDraft(long tenantId, long chartId) {
        ChartVersion value = draft(tenantId, chartId);
        if (value == null) {
            throw new ConflictException("图表没有可编辑草稿，请先修改以创建新版本");
        }
        return value;
    }

    private ChartVersion draft(long tenantId, long chartId) {
        return mapper.listChartVersions(tenantId, chartId).stream()
                .filter(value -> "DRAFT".equals(value.status()))
                .reduce((first, second) -> second).orElse(null);
    }

    private void requireRead(String authorization, Chart chart) {
        access.requireWorkspace(authorization, chart.tenantId(), chart.workspaceId());
        access.requirePermission(authorization, chart.tenantId(), chart.workspaceId(),
                "dashboard:read", "read");
    }

    private void requireManage(String authorization, Chart chart) {
        access.requireWorkspace(authorization, chart.tenantId(), chart.workspaceId());
        access.requirePermission(authorization, chart.tenantId(), chart.workspaceId(),
                "chart:manage", "manage");
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
