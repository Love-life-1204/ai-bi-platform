package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.AssetAclEntity;
import com.aibi.platform.identity.entity.AssetEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcAssetAccessMapper implements AssetAccessMapper {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAssetAccessMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AssetEntity insertAsset(AssetEntity asset) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_asset
                        (id, tenant_id, workspace_id, asset_type, asset_ref_id,
                         owner_member_id, asset_status)
                    VALUES
                        (:id, :tenantId, :workspaceId, :assetType, :assetRefId,
                         :ownerMemberId, :assetStatus)
                    """, Map.of(
                    "id", asset.id(), "tenantId", asset.tenantId(),
                    "workspaceId", asset.workspaceId(), "assetType", asset.assetType(),
                    "assetRefId", asset.assetRefId(), "ownerMemberId", asset.ownerMemberId(),
                    "assetStatus", asset.assetStatus()));
            return asset;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("资产已登记");
        }
    }

    @Override
    public Optional<AssetEntity> findAsset(long tenantId, long assetId) {
        List<AssetEntity> values = jdbcTemplate.query("""
                SELECT id, tenant_id, workspace_id, asset_type, asset_ref_id,
                       owner_member_id, asset_status
                  FROM sys_asset
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", assetId), this::mapAsset);
        return values.stream().findFirst();
    }

    @Override
    public Optional<AssetEntity> findAssetByReference(long tenantId, String assetType, long resourceId) {
        return jdbcTemplate.query("""
                SELECT id,tenant_id,workspace_id,asset_type,asset_ref_id,owner_member_id,asset_status
                  FROM sys_asset WHERE tenant_id=:tenant AND asset_type=:type AND asset_ref_id=:resource
                   AND deleted_at IS NULL
                """, Map.of("tenant", tenantId, "type", assetType, "resource", resourceId), this::mapAsset)
                .stream().findFirst();
    }

    @Override
    public AssetAclEntity setAcl(AssetAclEntity acl) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", acl.id()).addValue("tenantId", acl.tenantId())
                .addValue("assetId", acl.assetId()).addValue("principalType", acl.principalType())
                .addValue("principalId", acl.principalId()).addValue("actionCode", acl.actionCode())
                .addValue("effect", acl.effect())
                .addValue("expiresAt", acl.expiresAt() == null ? null : Timestamp.from(acl.expiresAt()));
        jdbcTemplate.update("""
                INSERT INTO sys_asset_acl
                    (id, tenant_id, asset_id, principal_type, principal_id,
                     action_code, effect, expires_at)
                VALUES
                    (:id, :tenantId, :assetId, :principalType, :principalId,
                     :actionCode, :effect, :expiresAt)
                ON DUPLICATE KEY UPDATE effect=:effect, expires_at=:expiresAt,
                                        updated_at=UTC_TIMESTAMP(3), lock_version=lock_version+1
                """, parameters);
        return acl;
    }

    @Override
    public List<AssetAclEntity> findAcls(long tenantId, long assetId, String actionCode) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, asset_id, principal_type, principal_id,
                       action_code, effect, expires_at
                  FROM sys_asset_acl
                 WHERE tenant_id=:tenantId AND asset_id=:assetId AND action_code=:actionCode
                   AND deleted_at IS NULL
                """, Map.of(
                "tenantId", tenantId, "assetId", assetId, "actionCode", actionCode), this::mapAcl);
    }

    private AssetEntity mapAsset(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AssetEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("workspace_id"), resultSet.getString("asset_type"),
                resultSet.getLong("asset_ref_id"), resultSet.getLong("owner_member_id"),
                resultSet.getString("asset_status"));
    }

    private AssetAclEntity mapAcl(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new AssetAclEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("asset_id"), resultSet.getString("principal_type"),
                resultSet.getLong("principal_id"), resultSet.getString("action_code"),
                resultSet.getString("effect"), expiresAt == null ? null : expiresAt.toInstant());
    }
}
