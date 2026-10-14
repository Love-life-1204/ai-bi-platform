package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @Positive @JsonResourceId long userId,
        @NotBlank @Size(max = 200) String memberName) {
}
