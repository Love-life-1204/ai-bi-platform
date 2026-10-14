package com.aibi.platform.identity.controller;

import com.aibi.platform.identity.dto.AcceptInvitationRequest;
import com.aibi.platform.identity.dto.MemberData;
import com.aibi.platform.identity.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/invitations", "/api/v1/invitations"})
@Tag(name = "接受邀请", description = "当前全局用户使用一次性令牌加入企业")
public class InvitationAcceptanceController {

    private final InvitationService invitationService;

    public InvitationAcceptanceController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    @Operation(summary = "接受成员邀请")
    public MemberData accept(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody AcceptInvitationRequest request) {
        return invitationService.accept(authorization, request);
    }
}
