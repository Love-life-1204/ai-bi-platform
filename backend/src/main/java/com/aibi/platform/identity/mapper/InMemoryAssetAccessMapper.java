package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.AssetAclEntity;
import com.aibi.platform.identity.entity.AssetEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAssetAccessMapper implements AssetAccessMapper {

    private final ConcurrentMap<Long, AssetEntity> assets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> assetIdsByReference = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AssetAclEntity> acls = new ConcurrentHashMap<>();

    @Override
    public AssetEntity insertAsset(AssetEntity asset) {
        String key = assetReferenceKey(asset.tenantId(), asset.assetType(), asset.assetRefId());
        if (assetIdsByReference.putIfAbsent(key, asset.id()) != null
                || assets.putIfAbsent(asset.id(), asset) != null) {
            assetIdsByReference.remove(key, asset.id());
            throw new ConflictException("资产已登记");
        }
        return asset;
    }

    @Override
    public Optional<AssetEntity> findAsset(long tenantId, long assetId) {
        return Optional.ofNullable(assets.get(assetId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public Optional<AssetEntity> findAssetByReference(long tenantId, String assetType, long resourceId) {
        Long id = assetIdsByReference.get(assetReferenceKey(tenantId, assetType, resourceId));
        return id == null ? Optional.empty() : findAsset(tenantId, id);
    }

    @Override
    public AssetAclEntity setAcl(AssetAclEntity acl) {
        acls.put(aclKey(acl.tenantId(), acl.assetId(), acl.principalType(),
                acl.principalId(), acl.actionCode()), acl);
        return acl;
    }

    @Override
    public List<AssetAclEntity> findAcls(long tenantId, long assetId, String actionCode) {
        return acls.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.assetId() == assetId)
                .filter(value -> value.actionCode().equals(actionCode))
                .sorted(Comparator.comparingLong(AssetAclEntity::id)).toList();
    }

    private String assetReferenceKey(long tenantId, String assetType, long assetRefId) {
        return tenantId + ":" + assetType + ":" + assetRefId;
    }

    private String aclKey(long tenantId, long assetId, String type, long principalId, String action) {
        return tenantId + ":" + assetId + ":" + type + ":" + principalId + ":" + action;
    }
}
