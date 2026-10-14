package com.aibi.platform.semantic.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateTermRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateTermRequest;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Term;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class TermService {

    private final SemanticMapper mapper;
    private final SemanticAccessManager access;

    public TermService(SemanticMapper mapper, SemanticAccessManager access) {
        this.mapper = mapper;
        this.access = access;
    }

    @MySqlTransactional(timeout = 5)
    public Term create(String authorization, long tenantId, CreateTermRequest request) {
        TokenService.TenantClaims claims = access.requireWorkspace(
                authorization, tenantId, request.workspaceId());
        access.requirePermission(authorization, tenantId, request.workspaceId(), "metric:manage", "manage");
        Instant now = Instant.now();
        return mapper.insertTerm(new Term(newId(), tenantId, request.workspaceId(), request.code(),
                request.name().trim(), request.definition().trim(), request.targetAssetId(),
                synonyms(request.synonyms()), "DRAFT", claims.memberId(), now, now, 0));
    }

    @MySqlTransactional(timeout = 5)
    public Term update(String authorization, long tenantId, long termId, UpdateTermRequest request) {
        Term term = required(tenantId, termId);
        access.requireWorkspace(authorization, tenantId, term.workspaceId());
        access.requirePermission(authorization, tenantId, term.workspaceId(), "metric:manage", "manage");
        if ("PUBLISHED".equals(term.status())) {
            throw new ConflictException("已发布业务术语不能原地修改");
        }
        return mapper.updateTerm(new Term(term.id(), term.tenantId(), term.workspaceId(), term.code(),
                request.name().trim(), request.definition().trim(), request.targetAssetId(),
                synonyms(request.synonyms()), "DRAFT", term.ownerMemberId(), term.createdAt(),
                Instant.now(), term.lockVersion() + 1), request.lockVersion());
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<Term> list(String authorization, long tenantId, Long workspaceId) {
        if (workspaceId != null) {
            access.requireWorkspace(authorization, tenantId, workspaceId);
            access.requirePermission(authorization, tenantId, workspaceId, "metric:read", "read");
            return mapper.listTerms(tenantId, workspaceId);
        }
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return mapper.listTerms(tenantId, workspaceId).stream()
                .filter(value -> access.canAccessWorkspace(
                        tenantId, value.workspaceId(), claims.memberId()))
                .filter(value -> access.hasPermission(authorization, tenantId, value.workspaceId(),
                        "metric:read", "read")).toList();
    }

    @MySqlTransactional(timeout = 5)
    public Term publish(String authorization, long tenantId, long termId) {
        Term term = required(tenantId, termId);
        access.requireWorkspace(authorization, tenantId, term.workspaceId());
        access.requirePermission(authorization, tenantId, term.workspaceId(), "metric:manage", "manage");
        if ("PUBLISHED".equals(term.status())) {
            return term;
        }
        return mapper.updateTerm(new Term(term.id(), term.tenantId(), term.workspaceId(), term.code(),
                term.name(), term.definition(), term.targetAssetId(), term.synonyms(), "PUBLISHED",
                term.ownerMemberId(), term.createdAt(), Instant.now(), term.lockVersion() + 1),
                term.lockVersion());
    }

    private Term required(long tenantId, long termId) {
        return mapper.findTerm(tenantId, termId)
                .orElseThrow(() -> new NotFoundException("业务术语不存在"));
    }

    private List<String> synonyms(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim).distinct().toList();
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
