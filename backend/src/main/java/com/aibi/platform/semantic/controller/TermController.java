package com.aibi.platform.semantic.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateTermRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateTermRequest;
import com.aibi.platform.semantic.model.SemanticModels.Term;
import com.aibi.platform.semantic.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/terms", "/api/v1/terms"})
@Tag(name = "业务术语", description = "业务术语及同义词维护")
public class TermController {

    private final TermService service;

    public TermController(TermService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增业务术语")
    public Term create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateTermRequest request) {
        return service.create(authorization, tenantId, request);
    }

    @GetMapping
    @Operation(summary = "查询业务术语词典")
    public List<Term> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId) {
        return service.list(authorization, tenantId, workspaceId);
    }

    @PatchMapping("/{termId}")
    @Operation(summary = "维护业务术语和同义词")
    public Term update(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long termId,
            @Valid @RequestBody UpdateTermRequest request) {
        return service.update(authorization, tenantId, termId, request);
    }

    @PostMapping("/{termId}/publish")
    @Operation(summary = "审核发布业务术语")
    public Term publish(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long termId) {
        return service.publish(authorization, tenantId, termId);
    }
}
