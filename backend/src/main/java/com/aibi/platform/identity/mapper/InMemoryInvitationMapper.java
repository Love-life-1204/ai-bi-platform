package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.InvitationEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryInvitationMapper implements InvitationMapper {

    private final ConcurrentMap<Long, InvitationEntity> invitations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByTokenHash = new ConcurrentHashMap<>();

    @Override
    public InvitationEntity insert(InvitationEntity invitation) {
        if (idsByTokenHash.putIfAbsent(invitation.tokenHash(), invitation.id()) != null
                || invitations.putIfAbsent(invitation.id(), invitation) != null) {
            idsByTokenHash.remove(invitation.tokenHash(), invitation.id());
            throw new ConflictException("邀请令牌冲突");
        }
        return invitation;
    }

    @Override
    public Optional<InvitationEntity> findByTokenHash(String tokenHash) {
        Long id = idsByTokenHash.get(tokenHash);
        return id == null ? Optional.empty() : Optional.ofNullable(invitations.get(id));
    }

    @Override
    public boolean accept(long invitationId, Instant acceptedAt, long expectedLockVersion) {
        final boolean[] accepted = {false};
        invitations.computeIfPresent(invitationId, (id, current) -> {
            if (!"PENDING".equals(current.invitationStatus())
                    || current.lockVersion() != expectedLockVersion) {
                return current;
            }
            accepted[0] = true;
            return new InvitationEntity(
                    current.id(), current.tenantId(), current.email(), current.tokenHash(),
                    current.inviterMemberId(), current.workspaceId(), current.roleId(),
                    current.expiresAt(), acceptedAt, "ACCEPTED", current.createdAt(),
                    acceptedAt, current.lockVersion() + 1);
        });
        return accepted[0];
    }
}
