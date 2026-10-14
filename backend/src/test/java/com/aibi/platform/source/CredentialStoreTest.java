package com.aibi.platform.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.source.model.DatasourceCredential;
import com.aibi.platform.source.security.EncryptedCredentialStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CredentialStoreTest {

    @Test
    void shouldExposeOnlyOpaqueReferenceAndRevokeSecret() {
        EncryptedCredentialStore store = new EncryptedCredentialStore(new ObjectMapper(), "");
        DatasourceCredential secret = new DatasourceCredential("reader", "very-secret-password");
        String reference = store.put(secret);

        assertThat(reference).startsWith("secret://datasource/").doesNotContain(secret.password());
        assertThat(store.read(reference)).isEqualTo(secret);

        store.delete(reference);
        assertThatThrownBy(() -> store.read(reference))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ApiErrorCode.DATASOURCE_AUTH_FAILED));
    }
}
