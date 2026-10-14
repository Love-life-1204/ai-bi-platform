package com.aibi.platform.query.store;

import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.model.QueryModels.QueryTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryQueryAuditRepository implements QueryAuditRepository {

    @Override
    public void created(QueryTask task, QueryPlan plan) {
        // The owning service retains the task in memory for the test/local-no-database profile.
    }

    @Override
    public void changed(QueryTask task) {
        // No external persistence in the explicit no-database profile.
    }
}
