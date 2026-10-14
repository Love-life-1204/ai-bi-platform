package com.aibi.platform.identity.security;

import com.aibi.platform.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String key, Instant now) {
        AttemptState state = attempts.get(key);
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
            throw new TooManyRequestsException(Duration.between(now, state.lockedUntil()).toSeconds() + 1);
        }
    }

    public void recordFailure(String key, Instant now) {
        AttemptState state = attempts.compute(key, (ignored, current) -> nextState(current, now));
        if (state.lockedUntil() != null) {
            throw new TooManyRequestsException(Duration.between(now, state.lockedUntil()).toSeconds() + 1);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private AttemptState nextState(AttemptState current, Instant now) {
        if (current == null || current.windowStartedAt().plus(WINDOW).isBefore(now)) {
            return new AttemptState(1, now, null);
        }
        int failures = current.failures() + 1;
        return new AttemptState(failures, current.windowStartedAt(),
                failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null);
    }

    private record AttemptState(int failures, Instant windowStartedAt, Instant lockedUntil) {
    }
}
