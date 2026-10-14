package com.aibi.platform.query.store;

import com.aibi.platform.query.config.QueryProperties;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class QueryResultCache {

    private final QueryProperties properties;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();

    public QueryResultCache(QueryProperties properties) {
        this.properties = properties;
    }

    public Optional<QueryResult> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            misses.increment();
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(Instant.now())) {
            entries.remove(key, entry);
            misses.increment();
            return Optional.empty();
        }
        hits.increment();
        return Optional.of(entry.result());
    }

    public synchronized void put(String key, QueryResult result) {
        Instant now = Instant.now();
        entries.entrySet().removeIf(value -> !value.getValue().expiresAt().isAfter(now));
        int maximum = Math.max(1, properties.getCacheMaxEntries());
        if (!entries.containsKey(key) && entries.size() >= maximum) {
            entries.entrySet().stream().min(Comparator.comparing(
                    value -> value.getValue().createdAt())).ifPresent(value -> {
                        if (entries.remove(value.getKey(), value.getValue())) {
                            evictions.increment();
                        }
                    });
        }
        entries.put(key, new Entry(result,
                now, now.plusSeconds(Math.max(1, properties.getCacheTtlSeconds()))));
    }

    public CacheStats stats() {
        return new CacheStats(entries.size(), hits.sum(), misses.sum(), evictions.sum());
    }

    public record CacheStats(int size, long hits, long misses, long evictions) {
    }

    private record Entry(QueryResult result, Instant createdAt, Instant expiresAt) {
    }
}
