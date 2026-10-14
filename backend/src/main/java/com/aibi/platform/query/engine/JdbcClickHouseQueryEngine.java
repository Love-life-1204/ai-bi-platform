package com.aibi.platform.query.engine;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.query.model.QueryModels.BoundParameter;
import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.query.model.QueryModels.EngineResult;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcClickHouseQueryEngine implements QueryEngine {

    private final DataSource dataSource;
    private final ConcurrentMap<String, PreparedStatement> active = new ConcurrentHashMap<>();

    public JdbcClickHouseQueryEngine(@Qualifier("clickHouseDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public EngineResult execute(String queryId, CompiledQuery query, int timeoutSeconds) {
        Instant started = Instant.now();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(query.sql())) {
            connection.setReadOnly(true);
            statement.setQueryTimeout(timeoutSeconds);
            setQueryId(statement, queryId);
            bind(statement, query.parameters());
            active.put(queryId, statement);
            try (ResultSet result = statement.executeQuery()) {
                return new EngineResult(rows(result), Duration.between(started, Instant.now()).toMillis());
            } finally {
                active.remove(queryId, statement);
            }
        } catch (SQLTimeoutException exception) {
            throw new ApiException(ApiErrorCode.QUERY_TIMEOUT);
        } catch (SQLException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ApiException(ApiErrorCode.QUERY_CANCELLED);
            }
            throw new ApiException(ApiErrorCode.QUERY_EXECUTION_FAILED);
        }
    }

    @Override
    public boolean cancel(String queryId) {
        PreparedStatement statement = active.get(queryId);
        if (statement == null) {
            return false;
        }
        try {
            statement.cancel();
            return true;
        } catch (SQLException exception) {
            throw new ApiException(ApiErrorCode.QUERY_EXECUTION_FAILED,
                    "数据库未能确认查询取消");
        }
    }

    private void bind(PreparedStatement statement, List<BoundParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            BoundParameter parameter = parameters.get(index);
            if (parameter.value() == null) {
                statement.setNull(index + 1, parameter.jdbcType());
            } else {
                statement.setObject(index + 1, parameter.value(), parameter.jdbcType());
            }
        }
    }

    private List<Map<String, Object>> rows(ResultSet result) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metadata = result.getMetaData();
        while (result.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                Object value = result.getObject(index);
                if (value instanceof BigDecimal decimal) {
                    value = decimal.toPlainString();
                } else if (value instanceof java.time.temporal.TemporalAccessor) {
                    value = value.toString();
                }
                row.put(metadata.getColumnLabel(index), value);
            }
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private void setQueryId(PreparedStatement statement, String queryId) {
        try {
            statement.getClass().getMethod("setQueryId", String.class).invoke(statement, queryId);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // JDBC cancel still targets the exact active statement if this driver omits setQueryId.
        }
    }
}
