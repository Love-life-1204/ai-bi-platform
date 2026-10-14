package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.InvitationEntity;
import java.time.Instant;
import java.util.Optional;

public interface InvitationMapper {

    InvitationEntity insert(InvitationEntity invitation);

    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    boolean accept(long invitationId, Instant acceptedAt, long expectedLockVersion);
}
