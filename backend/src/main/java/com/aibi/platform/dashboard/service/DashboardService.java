package com.aibi.platform.dashboard.service;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.dashboard.dto.DashboardDtos.CreateDashboardRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardData;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardQueryData;
import com.aibi.platform.dashboard.dto.DashboardDtos.DashboardQueryRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.UpdateDashboardRequest;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetError;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetInput;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetResult;
import com.aibi.platform.dashboard.manager.DashboardDefinitionValidator;
import com.aibi.platform.dashboard.mapper.DashboardMapper;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardWidget;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.query.dto.QueryDtos.Filter;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.service.QueryService;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final DashboardMapper mapper;
    private final SemanticAccessManager access;
    private final DashboardDefinitionValidator validator;
    private final QueryService queryService;

    public DashboardService(DashboardMapper mapper, SemanticAccessManager access,
            DashboardDefinitionValidator validator, QueryService queryService) {
        this.mapper = mapper;
        this.access = access;
        this.validator = validator;
        this.queryService = queryService;
    }

    @MySqlTransactional(timeout = 5)
    public DashboardData create(String authorization, long tenantId,
            CreateDashboardRequest request) {
        TokenService.TenantClaims claims = access.requireWorkspace(
                authorization, tenantId, request.workspaceId());
        access.requirePermission(authorization, tenantId, request.workspaceId(),
                "dashboard:manage", "manage");
        validator.validate(request.layout(), request.filters(), request.widgets());
        validateChartReferences(tenantId, request.workspaceId(), request.widgets(), false);
        Instant now = Instant.now();
        Dashboard dashboard = mapper.insertDashboard(new Dashboard(newId(), tenantId,
                request.workspaceId(), request.code(), request.name().trim(), claims.memberId(),
                null, "DRAFT", now, now, 0));
        long draftId = newId();
        DashboardVersion draft = mapper.insertDashboardVersion(new DashboardVersion(
                draftId, tenantId, dashboard.id(), 1, request.layout().deepCopy(),
                request.filters().deepCopy(), widgets(tenantId, draftId, request.widgets()),
                "DRAFT", null, 0));
        return new DashboardData(dashboard, null, draft);
    }

    @MySqlTransactional(timeout = 5)
    public DashboardData update(String authorization, long tenantId, long dashboardId,
            UpdateDashboardRequest request) {
        Dashboard dashboard = requiredDashboard(tenantId, dashboardId);
        requireManage(authorization, dashboard);
        validator.validate(request.layout(), request.filters(), request.widgets());
        validateChartReferences(tenantId, dashboard.workspaceId(), request.widgets(), false);
        Dashboard updated = mapper.updateDashboard(new Dashboard(
                dashboard.id(), dashboard.tenantId(), dashboard.workspaceId(), dashboard.code(),
                request.name().trim(), dashboard.ownerMemberId(), dashboard.currentVersionId(),
                "DRAFT", dashboard.createdAt(), Instant.now(), dashboard.lockVersion() + 1),
                request.lockVersion());
        DashboardVersion draft = draft(tenantId, dashboardId);
        if (draft == null) {
            int next = mapper.listDashboardVersions(tenantId, dashboardId).stream()
                    .mapToInt(DashboardVersion::versionNo).max().orElse(0) + 1;
            long draftId = newId();
            draft = mapper.insertDashboardVersion(new DashboardVersion(draftId, tenantId,
                    dashboardId, next, request.layout().deepCopy(), request.filters().deepCopy(),
                    widgets(tenantId, draftId, request.widgets()), "DRAFT", null, 0));
        } else {
            draft = mapper.updateDashboardVersion(new DashboardVersion(
                    draft.id(), tenantId, dashboardId, draft.versionNo(),
                    request.layout().deepCopy(), request.filters().deepCopy(),
                    widgets(tenantId, draft.id(), request.widgets()), "DRAFT", null,
                    draft.lockVersion() + 1), draft.lockVersion());
        }
        return new DashboardData(updated, current(tenantId, updated), draft);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<Dashboard> list(String authorization, long tenantId, Long workspaceId) {
        if (workspaceId != null) {
            access.requireWorkspace(authorization, tenantId, workspaceId);
            access.requirePermission(authorization, tenantId, workspaceId,
                    "dashboard:read", "read");
            return mapper.listDashboards(tenantId, workspaceId);
        }
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return mapper.listDashboards(tenantId, null).stream()
                .filter(value -> access.canAccessWorkspace(
                        tenantId, value.workspaceId(), claims.memberId()))
                .filter(value -> access.hasPermission(authorization, tenantId,
                        value.workspaceId(), "dashboard:read", "read")).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public DashboardData get(String authorization, long tenantId, long dashboardId) {
        Dashboard dashboard = requiredDashboard(tenantId, dashboardId);
        requireRead(authorization, dashboard);
        DashboardVersion visibleDraft = access.hasPermission(authorization, tenantId,
                dashboard.workspaceId(), "dashboard:manage", "manage")
                ? draft(tenantId, dashboardId) : null;
        return new DashboardData(dashboard, current(tenantId, dashboard), visibleDraft);
    }

    @MySqlTransactional(timeout = 5)
    public DashboardVersion publish(String authorization, long tenantId, long dashboardId) {
        Dashboard dashboard = requiredDashboard(tenantId, dashboardId);
        requireManage(authorization, dashboard);
        DashboardVersion draft = requiredDraft(tenantId, dashboardId);
        List<WidgetInput> inputs = draft.widgets().stream().map(value ->
                new WidgetInput(value.key(), value.chartVersionId(), value.position())).toList();
        validator.validate(draft.layout(), draft.filters(), inputs);
        validateChartReferences(tenantId, dashboard.workspaceId(), inputs, true);
        DashboardVersion published = mapper.updateDashboardVersion(new DashboardVersion(
                draft.id(), draft.tenantId(), draft.dashboardId(), draft.versionNo(),
                draft.layout(), draft.filters(), draft.widgets(), "PUBLISHED", Instant.now(),
                draft.lockVersion() + 1), draft.lockVersion());
        mapper.updateDashboard(new Dashboard(dashboard.id(), dashboard.tenantId(),
                dashboard.workspaceId(), dashboard.code(), dashboard.name(),
                dashboard.ownerMemberId(), published.id(), "PUBLISHED", dashboard.createdAt(),
                Instant.now(), dashboard.lockVersion() + 1), dashboard.lockVersion());
        return published;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<DashboardVersion> versions(String authorization, long tenantId, long dashboardId) {
        Dashboard dashboard = requiredDashboard(tenantId, dashboardId);
        requireRead(authorization, dashboard);
        boolean canManage = access.hasPermission(authorization, tenantId,
                dashboard.workspaceId(), "dashboard:manage", "manage");
        return mapper.listDashboardVersions(tenantId, dashboardId).stream()
                .filter(value -> canManage || "PUBLISHED".equals(value.status())).toList();
    }

    public DashboardQueryData query(String authorization, long tenantId, long dashboardId,
            DashboardQueryRequest request) {
        Dashboard dashboard = requiredDashboard(tenantId, dashboardId);
        requireRead(authorization, dashboard);
        DashboardVersion version;
        String mode;
        if (request.previewDraft()) {
            requireManage(authorization, dashboard);
            version = requiredDraft(tenantId, dashboardId);
            mode = "PREVIEW";
        } else {
            if (dashboard.currentVersionId() == null) {
                throw new ConflictException("看板尚未发布");
            }
            version = mapper.findDashboardVersion(tenantId, dashboard.currentVersionId())
                    .orElseThrow(() -> new NotFoundException("当前看板版本不存在"));
            mode = "PUBLISHED";
        }
        List<WidgetResult> results = version.widgets().stream().map(widget ->
                queryWidget(authorization, tenantId, widget, request)).toList();
        return new DashboardQueryData(dashboardId, version.id(), mode, results);
    }

    private WidgetResult queryWidget(String authorization, long tenantId,
            DashboardWidget widget, DashboardQueryRequest request) {
        ChartVersion version = mapper.findChartVersion(tenantId, widget.chartVersionId()).orElse(null);
        if (version == null) {
            return failed(widget, "RESOURCE_NOT_FOUND", "图表版本不存在", false, null);
        }
        try {
            QueryPlan plan = merge(version.queryPlan(), request);
            return new WidgetResult(widget.key(), version.id(), "SUCCEEDED",
                    queryService.execute(authorization, tenantId, plan), null, version.chartSpec());
        } catch (ApiException exception) {
            String code = exception.getErrorCode().getCode();
            return failed(widget, code, exception.getMessage(), retryable(exception), version.chartSpec());
        } catch (RuntimeException exception) {
            return failed(widget, ApiErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                    "图表加载失败", true, version.chartSpec());
        }
    }

    private QueryPlan merge(QueryPlan base, DashboardQueryRequest request) {
        List<Filter> filters = new ArrayList<>(base.filters());
        filters.addAll(request.filters());
        filters.addAll(request.interactionFilters());
        if (filters.size() > 50) {
            throw new BadRequestException("合并后的筛选条件不能超过 50 个");
        }
        return new QueryPlan(base.datasetVersionId(), base.metricVersionIds(),
                base.dimensionFieldIds(), request.timeRange() == null
                        ? base.timeRange() : request.timeRange(), filters, base.sort(), base.limit());
    }

    private boolean retryable(ApiException exception) {
        return switch (exception.getErrorCode()) {
            case QUERY_TIMEOUT, QUERY_EXECUTION_FAILED, TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR -> true;
            default -> false;
        };
    }

    private WidgetResult failed(DashboardWidget widget, String code, String message,
            boolean retryable, com.fasterxml.jackson.databind.JsonNode spec) {
        return new WidgetResult(widget.key(), widget.chartVersionId(), "FAILED", null,
                new WidgetError(code, message, retryable), spec);
    }

    private void validateChartReferences(long tenantId, long workspaceId,
            List<WidgetInput> widgets, boolean requirePublished) {
        for (WidgetInput input : widgets) {
            ChartVersion version = mapper.findChartVersion(tenantId, input.chartVersionId())
                    .orElseThrow(() -> new NotFoundException("图表版本不存在"));
            Chart chart = mapper.findChart(tenantId, version.chartId())
                    .orElseThrow(() -> new NotFoundException("图表不存在"));
            if (chart.workspaceId() != workspaceId) {
                throw new ConflictException("看板不能引用其他工作空间的图表");
            }
            if (requirePublished && !"PUBLISHED".equals(version.status())) {
                throw new ConflictException("正式看板只能引用已发布图表版本");
            }
        }
    }

    private List<DashboardWidget> widgets(long tenantId, long versionId,
            List<WidgetInput> inputs) {
        return inputs.stream().map(value -> new DashboardWidget(newId(), tenantId, versionId,
                value.chartVersionId(), value.key(), value.position().deepCopy(), 0)).toList();
    }

    private Dashboard requiredDashboard(long tenantId, long dashboardId) {
        return mapper.findDashboard(tenantId, dashboardId)
                .orElseThrow(() -> new NotFoundException("看板不存在"));
    }

    private DashboardVersion current(long tenantId, Dashboard dashboard) {
        if (dashboard.currentVersionId() == null) {
            return null;
        }
        return mapper.findDashboardVersion(tenantId, dashboard.currentVersionId())
                .orElseThrow(() -> new NotFoundException("当前看板版本不存在"));
    }

    private DashboardVersion requiredDraft(long tenantId, long dashboardId) {
        DashboardVersion value = draft(tenantId, dashboardId);
        if (value == null) {
            throw new ConflictException("看板没有可编辑草稿，请先修改以创建新版本");
        }
        return value;
    }

    private DashboardVersion draft(long tenantId, long dashboardId) {
        return mapper.listDashboardVersions(tenantId, dashboardId).stream()
                .filter(value -> "DRAFT".equals(value.status()))
                .reduce((first, second) -> second).orElse(null);
    }

    private void requireRead(String authorization, Dashboard dashboard) {
        access.requireWorkspace(authorization, dashboard.tenantId(), dashboard.workspaceId());
        access.requirePermission(authorization, dashboard.tenantId(), dashboard.workspaceId(),
                "dashboard:read", "read");
    }

    private void requireManage(String authorization, Dashboard dashboard) {
        access.requireWorkspace(authorization, dashboard.tenantId(), dashboard.workspaceId());
        access.requirePermission(authorization, dashboard.tenantId(), dashboard.workspaceId(),
                "dashboard:manage", "manage");
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
