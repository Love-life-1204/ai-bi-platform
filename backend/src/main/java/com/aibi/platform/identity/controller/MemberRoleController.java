package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.AssignMemberRoleRequest;
import com.aibi.platform.identity.dto.MemberRoleData;
import com.aibi.platform.identity.service.MemberRoleService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({
    "/api/tenants/{tenantId}/members/{memberId}/roles",
    "/api/v1/tenants/{tenantId}/members/{memberId}/roles"
})
@Tag(name = "成员角色", description = "成员角色授权与有效期管理")
public class MemberRoleController {

    private final MemberRoleService memberRoleService;

    public MemberRoleController(MemberRoleService memberRoleService) {
        this.memberRoleService = memberRoleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "分配成员角色")
    public MemberRoleData assign(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @Valid @RequestBody AssignMemberRoleRequest request) {
        return memberRoleService.assign(authorization, tenantId, memberId, request);
    }

    @GetMapping
    @Operation(summary = "查询成员角色及当前有效性")
    public List<MemberRoleData> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long memberId) {
        return memberRoleService.list(authorization, tenantId, memberId);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "撤销成员角色")
    public void revoke(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @PathVariable @Positive @JsonResourceId long assignmentId,
            @RequestParam @PositiveOrZero long memberLockVersion) {
        memberRoleService.revoke(authorization, tenantId, memberId, assignmentId, memberLockVersion);
    }
}
