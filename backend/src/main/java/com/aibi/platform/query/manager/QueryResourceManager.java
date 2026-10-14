package com.aibi.platform.query.manager;

import com.aibi.platform.common.exception.TooManyRequestsException;
import com.aibi.platform.query.config.QueryProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class QueryResourceManager {

    private final QueryProperties properties;
    private final Semaphore global;
    private final ConcurrentMap<Long, Semaphore> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicInteger> waiting = new ConcurrentHashMap<>();

    public QueryResourceManager(QueryProperties properties) {
        this.properties = properties;
        global = new Semaphore(positive(properties.getGlobalConcurrency()), true);
    }

    public Lease acquire(long tenantId) {
        Semaphore tenant = tenants.computeIfAbsent(tenantId,
                ignored -> new Semaphore(positive(properties.getTenantConcurrency()), true));
        if (global.tryAcquire()) {
            if (tenant.tryAcquire()) {
                return new Lease(global, tenant);
            }
            global.release();
        }
        AtomicInteger tenantWaiting = waiting.computeIfAbsent(tenantId, ignored -> new AtomicInteger());
        if (tenantWaiting.incrementAndGet() > properties.getQueueCapacity()) {
            tenantWaiting.decrementAndGet();
            throw new TooManyRequestsException(1);
        }
        boolean globalAcquired = false;
        boolean tenantAcquired = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
                    positive(properties.getTimeoutSeconds()));
            globalAcquired = global.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS);
            if (globalAcquired) {
                tenantAcquired = tenant.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS);
            }
            if (!globalAcquired || !tenantAcquired) {
                if (globalAcquired) {
                    global.release();
                }
                throw new TooManyRequestsException(1);
            }
            return new Lease(global, tenant);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (globalAcquired) {
                global.release();
            }
            throw new TooManyRequestsException(1);
        } finally {
            tenantWaiting.decrementAndGet();
        }
    }

    private int positive(int value) {
        return Math.max(1, value);
    }

    private long remaining(long deadline) {
        return Math.max(0, deadline - System.nanoTime());
    }

    public static final class Lease implements AutoCloseable {
        private final Semaphore global;
        private final Semaphore tenant;
        private boolean closed;

        private Lease(Semaphore global, Semaphore tenant) {
            this.global = global;
            this.tenant = tenant;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                tenant.release();
                global.release();
                closed = true;
            }
        }
    }
}
