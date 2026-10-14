package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.AuthorizeWorkspaceMemberRequest;
import com.aibi.platform.identity.dto.CreateWorkspaceRequest;
import com.aibi.platform.identity.dto.UpdateWorkspaceRequest;
import com.aibi.platform.identity.dto.WorkspaceData;
import com.aibi.platform.identity.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/workspaces", "/api/v1/tenants/{tenantId}/workspaces"})
@Tag(name = "工作空间", description = "工作空间与成员授权管理")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建工作空间")
    public WorkspaceData create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(authorization, tenantId, request);
    }

    @PutMapping("/{workspaceId}")
    @Operation(summary = "修改工作空间")
    public WorkspaceData update(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(authorization, tenantId, workspaceId, request);
    }

    @GetMapping
    @Operation(summary = "查询工作空间")
    public List<WorkspaceData> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId) {
        return workspaceService.list(authorization, tenantId);
    }

    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "授权工作空间成员")
    public void authorizeMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long workspaceId,
            @Valid @RequestBody AuthorizeWorkspaceMemberRequest request) {
        workspaceService.authorizeMember(authorization, tenantId, workspaceId, request);
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "撤销工作空间成员授权")
    public void revokeMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long workspaceId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @RequestParam @PositiveOrZero long memberLockVersion) {
        workspaceService.revokeMember(
                authorization, tenantId, workspaceId, memberId, memberLockVersion);
    }
}
