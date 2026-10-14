package com.aibi.platform.ingestion.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateImportRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.FileData;
import com.aibi.platform.ingestion.dto.IngestionDtos.FilePreviewData;
import com.aibi.platform.ingestion.dto.IngestionDtos.ImportJobData;
import com.aibi.platform.ingestion.entity.FileEntity;
import com.aibi.platform.ingestion.service.IngestionService;
import com.aibi.platform.identity.service.PermissionContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping({"/api", "/api/v1"})
@Tag(name = "文件与导入", description = "CSV/XLSX 安全上传、预览与导入任务")
public class FileImportController {

    private final IngestionService service;
    private final PermissionContextService permissionContextService;

    public FileImportController(IngestionService service,
            PermissionContextService permissionContextService) {
        this.service = service;
        this.permissionContextService = permissionContextService;
    }

    @PostMapping(value = "/files/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传并安全校验 CSV/XLSX")
    public FileData upload(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestParam(required = false) @Positive @JsonResourceId Long workspaceId,
            @RequestPart("file") MultipartFile file) {
        return service.upload(authorization, tenantId, workspaceId, file);
    }

    @GetMapping("/files/{fileId}/preview")
    @Operation(summary = "预览文件并推断字段类型和日期格式")
    public FilePreviewData preview(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long fileId) {
        return service.preview(authorization, tenantId, fileId);
    }

    @GetMapping("/files/{fileId}/content")
    @Operation(summary = "下载租户隔离的文件或错误报告")
    public ResponseEntity<Resource> content(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long fileId) {
        FileEntity file = service.file(authorization, tenantId, fileId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes()).body(new FileSystemResource(file.storagePath()));
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "创建文件导入任务")
    public ImportJobData createImport(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateImportRequest request) {
        long memberId = permissionContextService.requireContext(authorization, tenantId).memberId();
        CreateImportRequest trusted = new CreateImportRequest(request.workspaceId(),
                request.datasetId(), memberId, request.objectFileId(), request.mappings(),
                request.strictMode());
        return service.createImport(authorization, tenantId, idempotencyKey, trusted);
    }

    @GetMapping("/imports")
    @Operation(summary = "查询导入任务")
    public List<ImportJobData> imports(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return service.listImports(authorization, tenantId);
    }

    @GetMapping("/imports/{importId}")
    @Operation(summary = "查询导入状态及异常行统计")
    public ImportJobData importStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long importId) {
        return service.importStatus(authorization, tenantId, importId);
    }

    @PostMapping("/imports/{importId}/cancel")
    @Operation(summary = "取消尚未结束的导入任务")
    public ImportJobData cancelImport(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long importId) {
        return service.cancelImport(authorization, tenantId, importId);
    }
}
