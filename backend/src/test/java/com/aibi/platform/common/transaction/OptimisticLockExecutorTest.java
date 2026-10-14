package com.aibi.platform.common.transaction;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class OptimisticLockExecutorTest {

    @Test
    void shouldAcceptExactlyOneUpdatedRow() {
        assertThatCode(() -> OptimisticLockExecutor.verifyAffectedRows(1)).doesNotThrowAnyException();
    }

    @Test
    void shouldConvertStaleVersionToConflict() {
        assertThatThrownBy(() -> OptimisticLockExecutor.verifyAffectedRows(0))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void shouldRejectUpdatesAffectingMultipleRows() {
        assertThatThrownBy(() -> OptimisticLockExecutor.verifyAffectedRows(2))
                .isInstanceOf(IllegalStateException.class);
    }
}
