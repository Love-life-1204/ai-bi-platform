package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.AssetAclEntity;
import com.aibi.platform.identity.entity.AssetEntity;
import java.util.List;
import java.util.Optional;

public interface AssetAccessMapper {

    AssetEntity insertAsset(AssetEntity asset);

    Optional<AssetEntity> findAsset(long tenantId, long assetId);

    Optional<AssetEntity> findAssetByReference(long tenantId, String assetType, long resourceId);

    AssetAclEntity setAcl(AssetAclEntity acl);

    List<AssetAclEntity> findAcls(long tenantId, long assetId, String actionCode);
}
