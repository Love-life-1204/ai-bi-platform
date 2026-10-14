package com.aibi.platform.source.security;

import com.aibi.platform.source.model.DatasourceCredential;

public interface CredentialStore {
    String put(DatasourceCredential credential);

    DatasourceCredential read(String reference);

    void delete(String reference);
}
