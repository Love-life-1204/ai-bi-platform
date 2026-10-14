package com.aibi.platform.query.engine;

import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.query.model.QueryModels.EngineResult;

public interface QueryEngine {

    EngineResult execute(String queryId, CompiledQuery query, int timeoutSeconds);

    boolean cancel(String queryId);
}
