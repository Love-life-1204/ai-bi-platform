package com.aibi.platform.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibi.platform.query.engine.JdbcClickHouseQueryEngine;
import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcClickHouseQueryEngineTest {

    @Test
    void cancelTargetsTheActualActiveDatabaseStatement() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenAnswer(invocation -> {
            executing.countDown();
            cancelled.await(2, TimeUnit.SECONDS);
            throw new SQLException("cancelled");
        });
        doAnswer(invocation -> {
            cancelled.countDown();
            return null;
        }).when(statement).cancel();
        JdbcClickHouseQueryEngine engine = new JdbcClickHouseQueryEngine(dataSource);
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> {
            try {
                engine.execute("qry_cancel", new CompiledQuery(
                        "SELECT 1", List.of(), List.of(), "digest"), 30);
            } catch (RuntimeException ignored) {
                // Expected after the mocked database acknowledges cancellation.
            }
        });

        assertThat(executing.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(engine.cancel("qry_cancel")).isTrue();
        running.get(2, TimeUnit.SECONDS);

        verify(statement).cancel();
    }
}
