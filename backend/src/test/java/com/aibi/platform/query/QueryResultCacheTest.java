package com.aibi.platform.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibi.platform.query.config.QueryProperties;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.store.QueryResultCache;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryResultCacheTest {

    @Test
    void boundsEntriesAndReportsHitsMissesAndEvictions() {
        QueryProperties properties = new QueryProperties();
        properties.setCacheMaxEntries(2);
        QueryResultCache cache = new QueryResultCache(properties);

        cache.put("tenant-1:a", result("a"));
        cache.put("tenant-1:b", result("b"));
        assertThat(cache.get("tenant-1:a")).isPresent();
        cache.put("tenant-1:c", result("c"));
        assertThat(cache.get("missing")).isEmpty();

        assertThat(cache.stats().size()).isEqualTo(2);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
        assertThat(cache.stats().evictions()).isEqualTo(1);
    }

    private QueryResult result(String id) {
        return new QueryResult(id, "SUCCEEDED", List.of(), List.of(), 0, false,
                new PublicationSnapshot(1, 1, 1), 1);
    }
}
