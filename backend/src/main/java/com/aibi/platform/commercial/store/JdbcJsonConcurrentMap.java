package com.aibi.platform.commercial.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** A small persistent ConcurrentMap facade used to keep the commercial domain API profile-neutral. */
final class JdbcJsonConcurrentMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentMap<K, V> {

    private static final String SELECT_ONE = """
            SELECT value_type, payload_json
              FROM sys_commercial_state
             WHERE namespace_code = :namespace AND state_key = :stateKey
            """;
    private static final String UPSERT = """
            INSERT INTO sys_commercial_state
                (namespace_code, state_key, value_type, payload_json)
            VALUES (:namespace, :stateKey, :valueType, :payload)
            ON DUPLICATE KEY UPDATE value_type = VALUES(value_type),
                payload_json = VALUES(payload_json), updated_at = CURRENT_TIMESTAMP(3)
            """;
    private static final String INSERT = """
            INSERT IGNORE INTO sys_commercial_state
                (namespace_code, state_key, value_type, payload_json)
            VALUES (:namespace, :stateKey, :valueType, :payload)
            """;

    private final String namespace;
    private final Class<K> keyType;
    private final Class<V> valueType;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Map<String, Class<?>> allowedTypes;

    JdbcJsonConcurrentMap(String namespace, Class<K> keyType, Class<V> valueType,
            NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
            Map<String, Class<?>> allowedTypes) {
        this.namespace = namespace;
        this.keyType = keyType;
        this.valueType = valueType;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.allowedTypes = allowedTypes;
    }

    @Override
    public V get(Object key) {
        var rows = jdbc.query(SELECT_ONE, parameters(key), (resultSet, rowNum) -> read(
                resultSet.getString("value_type"), resultSet.getString("payload_json")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public V put(K key, V value) {
        V previous = get(key);
        jdbc.update(UPSERT, parameters(key, value));
        return previous;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        int inserted = jdbc.update(INSERT, parameters(key, value));
        return inserted == 1 ? null : get(key);
    }

    @Override
    public V remove(Object key) {
        V previous = get(key);
        if (previous != null) {
            jdbc.update("DELETE FROM sys_commercial_state WHERE namespace_code = :namespace"
                    + " AND state_key = :stateKey", parameters(key));
        }
        return previous;
    }

    @Override
    public boolean remove(Object key, Object value) {
        V current = get(key);
        if (!Objects.equals(current, value)) {
            return false;
        }
        return remove(key) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        V current = get(key);
        if (!Objects.equals(current, oldValue)) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    @Override
    public V replace(K key, V value) {
        V current = get(key);
        if (current == null) {
            return null;
        }
        put(key, value);
        return current;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        String sql = "SELECT state_key, value_type, payload_json FROM sys_commercial_state"
                + " WHERE namespace_code = :namespace ORDER BY state_key";
        return new LinkedHashSet<>(jdbc.query(sql, Map.of("namespace", namespace),
                (resultSet, rowNum) -> Map.entry(parseKey(resultSet.getString("state_key")),
                        read(resultSet.getString("value_type"),
                                resultSet.getString("payload_json")))));
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM sys_commercial_state WHERE namespace_code = :namespace",
                Map.of("namespace", namespace));
    }

    private MapSqlParameterSource parameters(Object key) {
        return new MapSqlParameterSource().addValue("namespace", namespace)
                .addValue("stateKey", String.valueOf(key));
    }

    private MapSqlParameterSource parameters(Object key, V value) {
        return parameters(key).addValue("valueType", value.getClass().getName())
                .addValue("payload", write(value));
    }

    @SuppressWarnings("unchecked")
    private V read(String storedType, String json) {
        Class<?> target = valueType == Object.class ? allowedTypes.get(storedType) : valueType;
        if (target == null) {
            throw new IllegalStateException("不允许反序列化商业化状态类型: " + storedType);
        }
        try {
            return (V) objectMapper.readValue(json, target);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商业化状态反序列化失败: " + namespace, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private K parseKey(String value) {
        if (keyType == Long.class) {
            return (K) Long.valueOf(value);
        }
        if (keyType == String.class) {
            return (K) value;
        }
        throw new IllegalStateException("不支持的商业化状态键类型: " + keyType.getName());
    }

    private String write(V value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商业化状态序列化失败: " + namespace, exception);
        }
    }
}
