package com.aibi.platform.mapper;

import com.aibi.platform.entity.SystemStatusEntity;

public interface SystemStatusMapper {

    SystemStatusEntity selectCurrentStatus();
}
