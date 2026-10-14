package com.aibi.platform.identity.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.identity.dto.AddMemberRequest;
import com.aibi.platform.identity.dto.MemberData;
import com.aibi.platform.identity.dto.ChangeMemberStatusRequest;
import com.aibi.platform.identity.service.MembershipService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
@Tag(name = "租户成员", description = "用户与企业成员关系管理")
public class MemberController {

    private final MembershipService membershipService;

    public MemberController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/tenants/{tenantId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "添加企业成员")
    public MemberData addMember(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody AddMemberRequest request) {
        return membershipService.addMember(authorization, tenantId, request);
    }

    @GetMapping("/users/me/memberships")
    @Operation(summary = "查询当前用户的企业成员关系")
    public List<MemberData> myMemberships(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return membershipService.myMemberships(authorization);
    }

    @GetMapping("/users/me/tenants")
    @Operation(summary = "查询本人有效成员关系中的企业摘要")
    public List<com.aibi.platform.identity.dto.TenantSummaryData> myTenants(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return membershipService.myTenants(authorization);
    }

    @GetMapping("/tenants/{tenantId}/members")
    @Operation(summary = "查询企业成员")
    public List<MemberData> members(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId) {
        return membershipService.members(authorization, tenantId);
    }

    @PostMapping("/tenants/{tenantId}/members/{memberId}/disable")
    @Operation(summary = "停用成员")
    public MemberData disableMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @Valid @RequestBody ChangeMemberStatusRequest request) {
        return membershipService.disable(authorization, tenantId, memberId, request);
    }

    @DeleteMapping("/tenants/{tenantId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "移除成员")
    public void removeMember(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long memberId,
            @RequestParam @jakarta.validation.constraints.PositiveOrZero long lockVersion) {
        membershipService.remove(authorization, tenantId, memberId, lockVersion);
    }
}
