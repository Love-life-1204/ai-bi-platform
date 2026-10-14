package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ChangeMemberStatusRequest(@PositiveOrZero long lockVersion) {
}
