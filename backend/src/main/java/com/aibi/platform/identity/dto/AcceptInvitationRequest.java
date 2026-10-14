package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 512) String inviteToken,
        @NotBlank @Size(max = 200) String memberName) {
}
