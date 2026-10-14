package com.aibi.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.DataPolicyService;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.DatasetData;
import com.aibi.platform.semantic.dto.SemanticDtos.FieldInput;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateDatasetRequest;
import com.aibi.platform.semantic.manager.DatasetPreviewManager;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.InMemorySemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.service.DatasetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetVersionLifecycleTest {

    @Test
    void publishedVersionStaysImmutableAndNextEditCreatesSuccessorDraft() {
        InMemorySemanticMapper mapper = new InMemorySemanticMapper();
        SemanticAccessManager access = mock(SemanticAccessManager.class);
        when(access.requireWorkspace("Bearer token", 1, 20)).thenReturn(
                new TokenService.TenantClaims(1, 2, 1, 99, 0, 0, Instant.now().plusSeconds(300)));
        DatasetService service = new DatasetService(mapper, access,
                new SemanticDefinitionManager(mapper, new ObjectMapper()), mock(DataPolicyService.class),
                new DatasetPreviewManager(), com.aibi.platform.source.DatasourceTestAccess.authorized());
        CreateDatasetRequest create = new CreateDatasetRequest(20, "orders", "订单", 100L,
                List.of(field("amount", "金额", "MEASURE")), List.of(), List.of());

        DatasetData created = service.create("Bearer token", 1, create);
        assertThat(created.draft().fields().get(0).displayFormat()).isEqualTo("#,##0.00");
        assertThat(service.validate("Bearer token", 1, created.dataset().id()).valid()).isTrue();
        DatasetVersion published = service.publish("Bearer token", 1, created.dataset().id());
        DatasetData current = service.get("Bearer token", 1, created.dataset().id());
        UpdateDatasetRequest update = new UpdateDatasetRequest("订单明细", 100L,
                List.of(field("amount", "实付金额", "MEASURE")), List.of(), List.of(),
                current.dataset().lockVersion());

        DatasetData edited = service.update("Bearer token", 1, created.dataset().id(), update);

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(edited.draft().versionNo()).isEqualTo(2);
        assertThat(edited.draft().status()).isEqualTo("DRAFT");
        assertThat(service.versions("Bearer token", 1, created.dataset().id()))
                .extracting(DatasetVersion::status).containsExactly("PUBLISHED", "DRAFT");
        assertThatThrownBy(() -> service.update("Bearer token", 1, created.dataset().id(), update))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    private FieldInput field(String key, String name, String role) {
        return new FieldInput(key, 1L, key, name, "DECIMAL", role, "INTERNAL", "#,##0.00");
    }
}
