package com.aibi.platform.messaging;

import com.aibi.platform.common.transaction.MySqlTransactional;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class InboxService {

    private final NamedParameterJdbcTemplate jdbc;

    public InboxService(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @MySqlTransactional
    public boolean execute(String consumerCode, TaskEnvelope envelope, Runnable handler) {
        try {
            jdbc.update("""
                    INSERT INTO sys_inbox_dedup
                        (id,tenant_id,consumer_code,event_key,processed_at,
                         created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:consumerCode,:eventKey,NOW(3),NOW(3),NOW(3),0)
                    """, Map.of(
                    "id", ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                    "tenantId", envelope.tenantId(),
                    "consumerCode", consumerCode,
                    "eventKey", envelope.eventId()));
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
        handler.run();
        return true;
    }
}
