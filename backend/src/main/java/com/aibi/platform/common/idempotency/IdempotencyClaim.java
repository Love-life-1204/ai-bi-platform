package com.aibi.platform.common.idempotency;

public record IdempotencyClaim(State state, String responseJson) {

    public enum State {
        ACQUIRED,
        REPLAY,
        IN_PROGRESS,
        HASH_CONFLICT
    }

    public static IdempotencyClaim acquired() {
        return new IdempotencyClaim(State.ACQUIRED, null);
    }

    public static IdempotencyClaim replay(String responseJson) {
        return new IdempotencyClaim(State.REPLAY, responseJson);
    }

    public static IdempotencyClaim inProgress() {
        return new IdempotencyClaim(State.IN_PROGRESS, null);
    }

    public static IdempotencyClaim hashConflict() {
        return new IdempotencyClaim(State.HASH_CONFLICT, null);
    }
}
