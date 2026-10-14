package com.aibi.platform.ingestion.service;

import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.mapper.AssetAccessMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.ResourceAccessManager;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Durable, server-captured identity; contains no bearer token or connector credential. */
@Component
public class IngestionTaskAuthorization {

    public record ResourceCheck(long workspaceId, String type, long id, String permission) { }
    public record Envelope(TaskAuthorizationSnapshot identity, List<ResourceCheck> resources) { }
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TaskAuthorizationService tasks;
    private final ResourceAccessManager access;
    private final AssetAccessMapper assets;
    private final com.aibi.platform.source.store.DatasourceStore sources;
    private final com.aibi.platform.semantic.mapper.SemanticMapper semantics;
    private final com.aibi.platform.ingestion.mapper.IngestionMapper ingestion;
    private final Map<String, Envelope> memory = new ConcurrentHashMap<>();

    public IngestionTaskAuthorization(@Qualifier("mysqlJdbcTemplate") ObjectProvider<NamedParameterJdbcTemplate> provider,
            ObjectMapper json, TaskAuthorizationService tasks, ResourceAccessManager access, AssetAccessMapper assets,
            com.aibi.platform.source.store.DatasourceStore sources,
            com.aibi.platform.semantic.mapper.SemanticMapper semantics,
            com.aibi.platform.ingestion.mapper.IngestionMapper ingestion) {
        this.jdbc = provider.getIfAvailable();
        this.json = json;
        this.tasks = tasks;
        this.access = access;
        this.assets = assets;
        this.sources = sources;
        this.semantics = semantics;
        this.ingestion = ingestion;
    }

    public void capture(String authorization, long tenant, String type, long id, long workspace,
            String action, List<ResourceCheck> resources) {
        capture(authorization, tenant, type, id, type, id, workspace, action, resources);
    }

    public void capture(String authorization, long tenant, String taskType, long taskId,
            String resourceType, long resourceId, long workspace, String action, List<ResourceCheck> resources) {
        for (var check : resources) {
            access.requireWorkspace(authorization, tenant, check.workspaceId(), check.permission());
            access.require(authorization, tenant, check.workspaceId(), check.type(), check.id(), check.permission());
        }
        var asset = assets.findAssetByReference(tenant, resourceType, resourceId).orElseThrow(ForbiddenException::new);
        var snapshot = tasks.capture(authorization, tenant, new AccessCheckRequest(workspace, action, asset.id(),
                action.substring(action.indexOf(':') + 1)));
        save(tenant, taskType, taskId, new Envelope(snapshot, List.copyOf(resources)));
    }

    public void require(long tenant, String type, long id) {
        Envelope envelope = load(tenant, type, id);
        var snapshot = envelope.identity();
        if (snapshot.tenantId() != tenant) {
            throw new ForbiddenException();
        }
        tasks.reauthorize(snapshot);
        var actor = new TokenService.TenantClaims(snapshot.sessionId(), snapshot.userId(), tenant, snapshot.memberId(),
                snapshot.tenantPermissionVersion(), snapshot.memberPermissionVersion(), Instant.now().plusSeconds(1));
        for (var check : envelope.resources()) {
            if (!currentResource(tenant, check) || !access.can(actor, check.workspaceId(), check.type(), check.id(), check.permission())) {
                throw new ForbiddenException();
            }
        }
    }

    private boolean currentResource(long tenant, ResourceCheck check) {
        return switch (check.type()) {
            case "DATASOURCE" -> sources.find(tenant, check.id())
                    .filter(source -> "ACTIVE".equals(source.status()) && source.workspaceId() == check.workspaceId()).isPresent();
            case "DATASET" -> semantics.findDataset(tenant, check.id())
                    .filter(dataset -> !"ARCHIVED".equals(dataset.status()) && dataset.workspaceId() == check.workspaceId()).isPresent();
            case "FILE" -> ingestion.findFile(tenant, check.id())
                    .filter(file -> "SAFE".equals(file.status()) && java.util.Objects.equals(file.workspaceId(), check.workspaceId())).isPresent();
            default -> false;
        };
    }

    public void copy(long tenant, String type, long from, long to) {
        require(tenant, type, from);
        save(tenant, type, to, load(tenant, type, from));
    }

    private Envelope load(long tenant, String type, long id) {
        if (jdbc == null) {
            var value = memory.get(key(tenant, type, id));
            if (value == null) {
                throw new ForbiddenException();
            }
            return value;
        }
        var values = jdbc.query("""
                SELECT snapshot_json FROM bi_ingestion_auth_snapshot
                 WHERE tenant_id=:tenant AND task_type=:type AND task_id=:id
                """, Map.of("tenant", tenant, "type", type, "id", id), (result, row) -> decode(result.getString(1)));
        if (values.size() != 1) {
            throw new ForbiddenException();
        }
        return values.get(0);
    }

    private void save(long tenant, String type, long id, Envelope value) {
        if (jdbc == null) {
            if (memory.putIfAbsent(key(tenant, type, id), value) != null) {
                throw new ForbiddenException();
            }
        } else {
            jdbc.update("""
                    INSERT INTO bi_ingestion_auth_snapshot(tenant_id,task_type,task_id,snapshot_json,created_at)
                    VALUES (:tenant,:type,:id,:snapshot,:now)
                    """, Map.of("tenant", tenant, "type", type, "id", id,
                    "snapshot", encode(value), "now", java.sql.Timestamp.from(Instant.now())));
        }
    }

    private String key(long tenant, String type, long id) { return tenant + ":" + type + ":" + id; }
    private String encode(Envelope value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Authorization snapshot serialization failed", exception); }
    }
    private Envelope decode(String value) {
        try { return json.readValue(value, Envelope.class); }
        catch (java.io.IOException exception) { throw new ForbiddenException(); }
    }
}
