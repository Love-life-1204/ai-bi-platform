package com.aibi.platform.query.engine;

import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.query.model.QueryModels.EngineResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryQueryEngine implements QueryEngine {

    @Override
    public EngineResult execute(String queryId, CompiledQuery query, int timeoutSeconds) {
        return new EngineResult(List.of(), 0);
    }

    @Override
    public boolean cancel(String queryId) {
        return false;
    }
}
