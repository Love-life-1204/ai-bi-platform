package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.CreateDepartmentRequest;
import com.aibi.platform.identity.dto.DepartmentData;
import com.aibi.platform.identity.dto.AssignDepartmentMemberRequest;
import com.aibi.platform.identity.dto.UpdateDepartmentRequest;
import com.aibi.platform.identity.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/departments", "/api/v1/tenants/{tenantId}/departments"})
@Tag(name = "部门", description = "租户部门层级管理")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增部门")
    public DepartmentData create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateDepartmentRequest request) {
        return departmentService.create(authorization, tenantId, request);
    }

    @PutMapping("/{departmentId}")
    @Operation(summary = "修改部门及上级关系")
    public DepartmentData update(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return departmentService.update(authorization, tenantId, departmentId, request);
    }

    @GetMapping("/tree")
    @Operation(summary = "查询部门树")
    public List<DepartmentData> tree(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId) {
        return departmentService.tree(authorization, tenantId);
    }

    @PostMapping("/{departmentId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "将成员加入部门")
    public void addMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long departmentId,
            @Valid @RequestBody AssignDepartmentMemberRequest request) {
        departmentService.addMember(authorization, tenantId, departmentId, request);
    }

    @DeleteMapping("/{departmentId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "将成员移出部门")
    public void removeMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long departmentId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @RequestParam @jakarta.validation.constraints.PositiveOrZero long memberLockVersion) {
        departmentService.removeMember(
                authorization, tenantId, departmentId, memberId, memberLockVersion);
    }
}
