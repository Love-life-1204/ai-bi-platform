package com.aibi.platform.ingestion.manager;

import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import java.time.Instant;

public interface SourceTableReader {

    SourcePage read(SyncJobEntity job, Instant lastUpdatedAt, String lastPrimaryKey,
            int limit);
}
