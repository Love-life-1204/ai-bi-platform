package com.aibi.platform.query.store;

import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.model.QueryModels.QueryTask;

public interface QueryAuditRepository {

    void created(QueryTask task, QueryPlan plan);

    void changed(QueryTask task);
}
