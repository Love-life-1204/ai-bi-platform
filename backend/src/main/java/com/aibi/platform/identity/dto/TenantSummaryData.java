package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
/** 全局会话只返回本人有效成员关系中的企业摘要。 */
public record TenantSummaryData(@JsonResourceId long id, String tenantCode, String tenantName, String tenantStatus) {
}
