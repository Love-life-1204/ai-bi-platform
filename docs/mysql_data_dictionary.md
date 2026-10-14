# MySQL 数据字典

依据数据库设计说明书重建；缺失的附件字段由本实现补充。共 79 张业务表。

通用字段：id 为应用生成 UInt64；created_by/updated_by 引用全局用户；时间为 UTC。
所有租户表含 tenant_id、UNIQUE(tenant_id,id)，删除父实体均 RESTRICT。
所有表含 created_at、updated_at、lock_version、deleted_at；不可变业务表的写权限另受账户限制。

## sys_user · 全局用户

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| email | VARCHAR(254) | 否 |  |  |
| display_name | VARCHAR(200) | 否 |  |  |
| password_hash | VARCHAR(255) | 是 |  |  |
| user_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| last_login_at | DATETIME(3) | 是 |  |  |

唯一键：email
查询索引：
CHECK：

外键：
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_tenant · 企业租户

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| tenant_code | VARCHAR(64) | 否 |  |  |
| tenant_name | VARCHAR(200) | 否 |  |  |
| tenant_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| timezone | VARCHAR(128) | 否 | 'Asia/Shanghai' |  |
| default_currency | CHAR(3) | 否 | 'CNY' |  |
| permission_version | BIGINT UNSIGNED | 否 | 1 |  |

唯一键：tenant_code
查询索引：
CHECK：

外键：
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_login_session · 登录会话

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| user_id | BIGINT UNSIGNED | 否 |  | sys_user |
| token_hash | CHAR(64) | 否 |  |  |
| expires_at | DATETIME(3) | 否 |  |  |
| revoked_at | DATETIME(3) | 是 |  |  |
| device_fingerprint | CHAR(64) | 是 |  |  |
| rotated_from_id | BIGINT UNSIGNED | 是 |  | sys_login_session |

唯一键：token_hash
查询索引：user_id,expires_at
CHECK：

外键：
- (user_id) → sys_user (id)
- (rotated_from_id) → sys_login_session (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_tenant_member · 租户成员

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| user_id | BIGINT UNSIGNED | 否 |  | sys_user |
| member_name | VARCHAR(200) | 否 |  |  |
| member_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| is_owner | TINYINT UNSIGNED | 否 | 0 |  |
| permission_version | BIGINT UNSIGNED | 否 | 1 |  |
| joined_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,user_id
查询索引：tenant_id,member_status,created_at
CHECK：is_owner IN (0,1)

外键：
- (tenant_id) → sys_tenant (id)
- (user_id) → sys_user (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_department · 部门

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| parent_id | BIGINT UNSIGNED | 是 |  | sys_department |
| department_code | VARCHAR(64) | 否 |  |  |
| department_name | VARCHAR(200) | 否 |  |  |
| sort_order | INT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,department_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,parent_id) → sys_department (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_member_department · 成员部门

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| department_id | BIGINT UNSIGNED | 否 |  | sys_department |
| is_primary | TINYINT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,member_id,department_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,department_id) → sys_department (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_workspace · 工作空间

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_code | VARCHAR(64) | 否 |  |  |
| workspace_name | VARCHAR(200) | 否 |  |  |
| workspace_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |

唯一键：tenant_id,workspace_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_workspace_member · 空间成员

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| member_status | VARCHAR(32) | 否 | 'ACTIVE' |  |

唯一键：tenant_id,workspace_id,member_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_invitation · 成员邀请

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| email | VARCHAR(254) | 否 |  |  |
| invite_token_hash | CHAR(64) | 否 |  |  |
| inviter_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| workspace_id | BIGINT UNSIGNED | 是 |  | sys_workspace |
| role_id | BIGINT UNSIGNED | 是 |  | sys_role |
| expires_at | DATETIME(3) | 否 |  |  |
| accepted_at | DATETIME(3) | 是 |  |  |
| invitation_status | VARCHAR(32) | 否 | 'PENDING' |  |

唯一键：tenant_id,invite_token_hash
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,inviter_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,role_id) → sys_role (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_tenant_setting · 租户配置

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| setting_key | VARCHAR(64) | 否 |  |  |
| setting_value | JSON | 否 |  |  |
| setting_version | BIGINT UNSIGNED | 否 | 1 |  |

唯一键：tenant_id,setting_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_tenant_resource · 租户资源路由

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| resource_type | VARCHAR(32) | 否 |  |  |
| resource_alias | VARCHAR(64) | 否 |  |  |
| locator_json | JSON | 否 |  |  |
| credential_ref | VARCHAR(255) | 是 |  |  |
| resource_status | VARCHAR(32) | 否 | 'ACTIVE' |  |

唯一键：tenant_id,resource_type,resource_alias
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_permission · 权限字典

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| permission_code | VARCHAR(128) | 否 |  |  |
| permission_name | VARCHAR(200) | 否 |  |  |
| resource_type | VARCHAR(32) | 否 |  |  |
| action_code | VARCHAR(64) | 否 |  |  |

唯一键：permission_code
查询索引：
CHECK：

外键：
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_role · 租户角色

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| role_code | VARCHAR(64) | 否 |  |  |
| role_name | VARCHAR(200) | 否 |  |  |
| role_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| is_builtin | TINYINT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,role_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_role_permission · 角色权限

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| role_id | BIGINT UNSIGNED | 否 |  | sys_role |
| permission_id | BIGINT UNSIGNED | 否 |  | sys_permission |
| effect | VARCHAR(32) | 否 | 'ALLOW' |  |

唯一键：tenant_id,role_id,permission_id
查询索引：
CHECK：effect IN ('ALLOW','DENY')

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,role_id) → sys_role (tenant_id,id)
- (permission_id) → sys_permission (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_member_role · 成员角色作用域

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| role_id | BIGINT UNSIGNED | 否 |  | sys_role |
| workspace_id | BIGINT UNSIGNED | 是 |  | sys_workspace |
| scope_key | VARCHAR(64) | 否 | 'TENANT' |  |
| expires_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,member_id,role_id,scope_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,role_id) → sys_role (tenant_id,id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_asset · 授权资产

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| asset_type | VARCHAR(32) | 否 |  |  |
| asset_ref_id | BIGINT UNSIGNED | 否 |  |  |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| asset_status | VARCHAR(32) | 否 | 'ACTIVE' |  |

唯一键：tenant_id,asset_type,asset_ref_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## sys_asset_acl · 资产授权

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| asset_id | BIGINT UNSIGNED | 否 |  | sys_asset |
| principal_type | VARCHAR(32) | 否 |  |  |
| principal_id | BIGINT UNSIGNED | 否 |  |  |
| action_code | VARCHAR(64) | 否 |  |  |
| effect | VARCHAR(32) | 否 |  |  |
| expires_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,asset_id,principal_type,principal_id,action_code
查询索引：
CHECK：effect IN ('ALLOW','DENY')；principal_type IN ('MEMBER','ROLE','DEPARTMENT')

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,asset_id) → sys_asset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_data_policy · 行级策略

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| policy_code | VARCHAR(64) | 否 |  |  |
| policy_name | VARCHAR(200) | 否 |  |  |
| policy_ast | JSON | 否 |  |  |
| policy_group | VARCHAR(64) | 否 |  |  |
| policy_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| policy_version | BIGINT UNSIGNED | 否 | 1 |  |

唯一键：tenant_id,policy_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_policy_grant · 行策略授权

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| policy_id | BIGINT UNSIGNED | 否 |  | sys_data_policy |
| principal_type | VARCHAR(32) | 否 |  |  |
| principal_id | BIGINT UNSIGNED | 否 |  |  |
| expires_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,policy_id,principal_type,principal_id
查询索引：
CHECK：principal_type IN ('MEMBER','ROLE','DEPARTMENT')

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,policy_id) → sys_data_policy (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_column_policy · 列权限

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_field_id | BIGINT UNSIGNED | 否 |  | bi_dataset_field |
| principal_type | VARCHAR(32) | 否 |  |  |
| principal_id | BIGINT UNSIGNED | 否 |  |  |
| access_mode | VARCHAR(32) | 否 |  |  |
| mask_config | JSON | 是 |  |  |

唯一键：tenant_id,dataset_field_id,principal_type,principal_id
查询索引：
CHECK：access_mode IN ('VISIBLE','MASKED','DENY')；principal_type IN ('MEMBER','ROLE','DEPARTMENT')

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_field_id) → bi_dataset_field (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_identity_provider · 企业身份源

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| provider_type | VARCHAR(32) | 否 |  |  |
| issuer_hash | CHAR(64) | 否 |  |  |
| issuer_url | VARCHAR(512) | 否 |  |  |
| client_id | VARCHAR(128) | 否 |  |  |
| credential_ref | VARCHAR(255) | 否 |  |  |
| config_json | JSON | 否 |  |  |
| provider_status | VARCHAR(32) | 否 | 'DISABLED' |  |

唯一键：tenant_id,provider_type,issuer_hash
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_datasource · 只读数据源

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| datasource_code | VARCHAR(64) | 否 |  |  |
| datasource_name | VARCHAR(200) | 否 |  |  |
| datasource_type | VARCHAR(32) | 否 |  |  |
| endpoint_json | JSON | 否 |  |  |
| credential_ref | VARCHAR(255) | 是 |  |  |
| datasource_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：tenant_id,datasource_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_source_object · 源数据对象

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| datasource_id | BIGINT UNSIGNED | 否 |  | bi_datasource |
| schema_name | VARCHAR(128) | 否 |  |  |
| object_name | VARCHAR(128) | 否 |  |  |
| object_type | VARCHAR(32) | 否 |  |  |
| schema_hash | CHAR(64) | 否 |  |  |
| metadata_json | JSON | 是 |  |  |

唯一键：tenant_id,datasource_id,schema_name,object_name
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,datasource_id) → bi_datasource (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_source_column · 源字段

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| source_object_id | BIGINT UNSIGNED | 否 |  | bi_source_object |
| column_name | VARCHAR(128) | 否 |  |  |
| stable_column_key | VARCHAR(64) | 否 |  |  |
| ordinal_position | INT UNSIGNED | 否 |  |  |
| data_type | VARCHAR(128) | 否 |  |  |
| is_nullable | TINYINT UNSIGNED | 否 |  |  |
| is_primary_key | TINYINT UNSIGNED | 否 | 0 |  |
| sensitivity | VARCHAR(32) | 否 | 'UNKNOWN' |  |
| column_comment | TEXT | 是 |  |  |

唯一键：tenant_id,source_object_id,column_name
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,source_object_id) → bi_source_object (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_metadata_snapshot · 元数据快照

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| datasource_id | BIGINT UNSIGNED | 否 |  | bi_datasource |
| snapshot_version | BIGINT UNSIGNED | 否 |  |  |
| schema_hash | CHAR(64) | 否 |  |  |
| snapshot_json | JSON | 否 |  |  |
| diff_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| collected_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,datasource_id,snapshot_version
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,datasource_id) → bi_datasource (tenant_id,id)
- (tenant_id,diff_object_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_import_job · 文件导入任务

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| object_file_id | BIGINT UNSIGNED | 否 |  | sys_object_file |
| requester_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| request_key | VARCHAR(128) | 否 |  |  |
| mapping_json | JSON | 是 |  |  |
| import_status | VARCHAR(32) | 否 | 'PENDING' |  |
| total_rows | BIGINT UNSIGNED | 否 | 0 |  |
| error_rows | BIGINT UNSIGNED | 否 | 0 |  |
| error_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |

唯一键：tenant_id,request_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (tenant_id,object_file_id) → sys_object_file (tenant_id,id)
- (tenant_id,requester_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,error_object_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_sync_job · 同步作业

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| datasource_id | BIGINT UNSIGNED | 否 |  | bi_datasource |
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| job_code | VARCHAR(64) | 否 |  |  |
| sync_mode | VARCHAR(32) | 否 |  |  |
| schedule_expression | VARCHAR(128) | 是 |  |  |
| timezone | VARCHAR(128) | 否 | 'Asia/Shanghai' |  |
| config_json | JSON | 否 |  |  |
| job_status | VARCHAR(32) | 否 | 'DISABLED' |  |

唯一键：tenant_id,job_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,datasource_id) → bi_datasource (tenant_id,id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_sync_watermark · 发布水位

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| sync_job_id | BIGINT UNSIGNED | 否 |  | bi_sync_job |
| last_updated_at | DATETIME(3) | 是 |  |  |
| last_primary_key | VARCHAR(255) | 是 |  |  |
| watermark_json | JSON | 是 |  |  |
| last_batch_id | BIGINT UNSIGNED | 是 |  | bi_batch |

唯一键：tenant_id,sync_job_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,sync_job_id) → bi_sync_job (tenant_id,id)
- (tenant_id,last_batch_id) → bi_batch (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_sync_run · 同步运行与租约

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| sync_job_id | BIGINT UNSIGNED | 否 |  | bi_sync_job |
| attempt_no | INT UNSIGNED | 否 |  |  |
| request_key | VARCHAR(128) | 否 |  |  |
| run_status | VARCHAR(32) | 否 | 'PENDING' |  |
| lease_owner | VARCHAR(128) | 是 |  |  |
| lease_until | DATETIME(3) | 是 |  |  |
| fencing_token | BIGINT UNSIGNED | 否 | 0 |  |
| started_at | DATETIME(3) | 是 |  |  |
| finished_at | DATETIME(3) | 是 |  |  |
| error_code | VARCHAR(64) | 是 |  |  |
| error_summary | TEXT | 是 |  |  |

唯一键：tenant_id,request_key
查询索引：tenant_id,run_status,lease_until
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,sync_job_id) → bi_sync_job (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_batch · 发布批次

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| sync_run_id | BIGINT UNSIGNED | 是 |  | bi_sync_run |
| import_job_id | BIGINT UNSIGNED | 是 |  | bi_import_job |
| batch_code | VARCHAR(128) | 否 |  |  |
| generation_id | BIGINT UNSIGNED | 否 |  |  |
| publish_seq | BIGINT UNSIGNED | 否 |  |  |
| batch_status | VARCHAR(32) | 否 | 'PENDING' |  |
| expected_rows | BIGINT UNSIGNED | 否 | 0 |  |
| actual_rows | BIGINT UNSIGNED | 否 | 0 |  |
| content_hash | CHAR(64) | 是 |  |  |
| quality_status | VARCHAR(32) | 否 | 'PENDING' |  |
| fencing_token | BIGINT UNSIGNED | 否 | 0 |  |
| ready_at | DATETIME(3) | 是 |  |  |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,batch_code；tenant_id,dataset_id,generation_id,publish_seq；tenant_id,dataset_id,generation_id,publish_seq,id
查询索引：tenant_id,batch_status,created_at
CHECK：publish_seq >= 1

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (tenant_id,sync_run_id) → bi_sync_run (tenant_id,id)
- (tenant_id,import_job_id) → bi_import_job (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_quality_rule · 质量规则

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| rule_code | VARCHAR(64) | 否 |  |  |
| rule_type | VARCHAR(32) | 否 |  |  |
| rule_config | JSON | 否 |  |  |
| severity | VARCHAR(32) | 否 | 'BLOCK' |  |
| rule_status | VARCHAR(32) | 否 | 'ACTIVE' |  |

唯一键：tenant_id,dataset_id,rule_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_quality_result · 质量检查证据

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| batch_id | BIGINT UNSIGNED | 否 |  | bi_batch |
| quality_rule_id | BIGINT UNSIGNED | 否 |  | bi_quality_rule |
| result_status | VARCHAR(32) | 否 |  |  |
| observed_value | JSON | 是 |  |  |
| expected_value | JSON | 是 |  |  |
| evidence_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| checked_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,batch_id,quality_rule_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,batch_id) → bi_batch (tenant_id,id)
- (tenant_id,quality_rule_id) → bi_quality_rule (tenant_id,id)
- (tenant_id,evidence_object_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dataset · 稳定数据集

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| dataset_code | VARCHAR(64) | 否 |  |  |
| dataset_name | VARCHAR(200) | 否 |  |  |
| dataset_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| current_version_id | BIGINT UNSIGNED | 是 |  | bi_dataset_version |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |

唯一键：tenant_id,workspace_id,dataset_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,current_version_id) → bi_dataset_version (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,id,current_version_id) → bi_dataset_version (tenant_id,dataset_id,id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_dataset_version · 数据集语义版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| version_no | INT UNSIGNED | 否 |  |  |
| source_object_id | BIGINT UNSIGNED | 是 |  | bi_source_object |
| definition_json | JSON | 否 |  |  |
| definition_hash | CHAR(64) | 否 |  |  |
| version_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,dataset_id,version_no；tenant_id,dataset_id,id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (tenant_id,source_object_id) → bi_source_object (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dataset_field · 语义字段

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| stable_field_key | VARCHAR(64) | 否 |  |  |
| field_name | VARCHAR(200) | 否 |  |  |
| data_type | VARCHAR(128) | 否 |  |  |
| source_column_id | BIGINT UNSIGNED | 是 |  | bi_source_column |
| expression_ast | JSON | 是 |  |  |
| sensitivity | VARCHAR(32) | 否 | 'INTERNAL' |  |
| is_dimension | TINYINT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,dataset_version_id,stable_field_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (tenant_id,source_column_id) → bi_source_column (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dataset_relation · 数据集关联

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| related_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| relation_code | VARCHAR(64) | 否 |  |  |
| cardinality | VARCHAR(32) | 否 |  |  |
| join_ast | JSON | 否 |  |  |
| effective_from | DATETIME(3) | 是 |  |  |
| effective_to | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,dataset_version_id,relation_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (tenant_id,related_version_id) → bi_dataset_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dataset_publication · 物理发布指针

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dataset_id | BIGINT UNSIGNED | 否 |  | bi_dataset |
| active_generation | BIGINT UNSIGNED | 是 |  |  |
| committed_publish_seq | BIGINT UNSIGNED | 否 | 0 |  |
| data_quality_version | BIGINT UNSIGNED | 否 | 0 |  |
| last_batch_id | BIGINT UNSIGNED | 是 |  | bi_batch |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,dataset_id
查询索引：
CHECK：(active_generation IS NULL AND committed_publish_seq = 0) OR (active_generation IS NOT NULL AND committed_publish_seq >= 1)

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dataset_id) → bi_dataset (tenant_id,id)
- (tenant_id,last_batch_id) → bi_batch (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,dataset_id,active_generation,committed_publish_seq,last_batch_id) → bi_batch (tenant_id,dataset_id,generation_id,publish_seq,id)

## bi_metric · 稳定指标

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| metric_code | VARCHAR(64) | 否 |  |  |
| metric_name | VARCHAR(200) | 否 |  |  |
| metric_unit | VARCHAR(32) | 否 | 'COUNT' |  |
| metric_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| current_version_id | BIGINT UNSIGNED | 是 |  | bi_metric_version |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |

唯一键：tenant_id,workspace_id,metric_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,current_version_id) → bi_metric_version (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,id,current_version_id) → bi_metric_version (tenant_id,metric_id,id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_metric_version · 指标定义版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| metric_id | BIGINT UNSIGNED | 否 |  | bi_metric |
| version_no | INT UNSIGNED | 否 |  |  |
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| formula_ast | JSON | 否 |  |  |
| time_spec_json | JSON | 否 |  |  |
| currency_spec_json | JSON | 否 |  |  |
| allowed_dimensions_json | JSON | 否 |  |  |
| precision_scale | INT UNSIGNED | 否 | 2 |  |
| definition_hash | CHAR(64) | 否 |  |  |
| version_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,metric_id,version_no；tenant_id,metric_id,id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,metric_id) → bi_metric (tenant_id,id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_metric_dependency · 指标依赖

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| metric_version_id | BIGINT UNSIGNED | 否 |  | bi_metric_version |
| depends_metric_version_id | BIGINT UNSIGNED | 否 |  | bi_metric_version |

唯一键：tenant_id,metric_version_id,depends_metric_version_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,metric_version_id) → bi_metric_version (tenant_id,id)
- (tenant_id,depends_metric_version_id) → bi_metric_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_term · 业务术语

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| term_code | VARCHAR(64) | 否 |  |  |
| term_name | VARCHAR(200) | 否 |  |  |
| definition | TEXT | 否 |  |  |
| target_asset_id | BIGINT UNSIGNED | 是 |  | sys_asset |
| review_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：tenant_id,workspace_id,term_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,target_asset_id) → sys_asset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_term_synonym · 术语同义词

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| term_id | BIGINT UNSIGNED | 否 |  | bi_term |
| synonym_text | VARCHAR(200) | 否 |  |  |

唯一键：tenant_id,term_id,synonym_text
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,term_id) → bi_term (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_chart · 图表

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| chart_code | VARCHAR(64) | 否 |  |  |
| chart_name | VARCHAR(200) | 否 |  |  |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| current_version_id | BIGINT UNSIGNED | 是 |  | bi_chart_version |
| chart_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：tenant_id,workspace_id,chart_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,current_version_id) → bi_chart_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,id,current_version_id) → bi_chart_version (tenant_id,chart_id,id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_chart_version · 图表版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| chart_id | BIGINT UNSIGNED | 否 |  | bi_chart |
| version_no | INT UNSIGNED | 否 |  |  |
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| query_plan | JSON | 否 |  |  |
| chart_spec | JSON | 否 |  |  |
| version_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,chart_id,version_no；tenant_id,chart_id,id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,chart_id) → bi_chart (tenant_id,id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dashboard · 看板

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| dashboard_code | VARCHAR(64) | 否 |  |  |
| dashboard_name | VARCHAR(200) | 否 |  |  |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| current_version_id | BIGINT UNSIGNED | 是 |  | bi_dashboard_version |
| dashboard_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：tenant_id,workspace_id,dashboard_code
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,current_version_id) → bi_dashboard_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,id,current_version_id) → bi_dashboard_version (tenant_id,dashboard_id,id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_dashboard_version · 看板版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dashboard_id | BIGINT UNSIGNED | 否 |  | bi_dashboard |
| version_no | INT UNSIGNED | 否 |  |  |
| layout_json | JSON | 否 |  |  |
| filters_json | JSON | 否 |  |  |
| version_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| published_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,dashboard_id,version_no；tenant_id,dashboard_id,id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dashboard_id) → bi_dashboard (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_dashboard_widget · 看板组件

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| dashboard_version_id | BIGINT UNSIGNED | 否 |  | bi_dashboard_version |
| chart_version_id | BIGINT UNSIGNED | 否 |  | bi_chart_version |
| widget_key | VARCHAR(64) | 否 |  |  |
| position_json | JSON | 否 |  |  |

唯一键：tenant_id,dashboard_version_id,widget_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,dashboard_version_id) → bi_dashboard_version (tenant_id,id)
- (tenant_id,chart_version_id) → bi_chart_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_share · 内部受控共享

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| asset_id | BIGINT UNSIGNED | 否 |  | sys_asset |
| sharer_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| recipient_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| token_hash | CHAR(64) | 否 |  |  |
| expires_at | DATETIME(3) | 否 |  |  |
| revoked_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,token_hash
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,asset_id) → sys_asset (tenant_id,id)
- (tenant_id,sharer_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,recipient_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_query_task · 受控查询任务

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| request_key | VARCHAR(128) | 否 |  |  |
| permission_version | BIGINT UNSIGNED | 否 |  |  |
| generation_id | BIGINT UNSIGNED | 否 |  |  |
| committed_publish_seq | BIGINT UNSIGNED | 否 |  |  |
| metric_versions_json | JSON | 否 |  |  |
| query_plan | JSON | 否 |  |  |
| plan_hash | CHAR(64) | 否 |  |  |
| engine_query_id | VARCHAR(128) | 是 |  |  |
| query_status | VARCHAR(32) | 否 | 'PENDING' |  |
| started_at | DATETIME(3) | 是 |  |  |
| finished_at | DATETIME(3) | 是 |  |  |
| error_code | VARCHAR(64) | 是 |  |  |
| row_count | BIGINT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,member_id,request_key
查询索引：tenant_id,query_status,created_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_query_result_ref · 查询结果引用

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| query_task_id | BIGINT UNSIGNED | 否 |  | bi_query_task |
| object_file_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| cache_key_hash | CHAR(64) | 是 |  |  |
| permission_version | BIGINT UNSIGNED | 否 |  |  |
| expires_at | DATETIME(3) | 否 |  |  |
| result_hash | CHAR(64) | 否 |  |  |
| row_count | BIGINT UNSIGNED | 否 |  |  |

唯一键：tenant_id,query_task_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,query_task_id) → bi_query_task (tenant_id,id)
- (tenant_id,object_file_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_saved_query · 保存查询

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| query_name | VARCHAR(200) | 否 |  |  |
| dataset_version_id | BIGINT UNSIGNED | 否 |  | bi_dataset_version |
| query_plan | JSON | 否 |  |  |

唯一键：
查询索引：tenant_id,workspace_id,created_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,dataset_version_id) → bi_dataset_version (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_report · 分析报告

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| report_code | VARCHAR(64) | 否 |  |  |
| version_no | INT UNSIGNED | 否 |  |  |
| report_name | VARCHAR(200) | 否 |  |  |
| query_task_id | BIGINT UNSIGNED | 是 |  | bi_query_task |
| content_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| evidence_json | JSON | 否 |  |  |
| report_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：tenant_id,workspace_id,report_code,version_no
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,query_task_id) → bi_query_task (tenant_id,id)
- (tenant_id,content_object_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_export_task · 导出任务

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| requester_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| report_id | BIGINT UNSIGNED | 是 |  | bi_report |
| query_task_id | BIGINT UNSIGNED | 是 |  | bi_query_task |
| request_key | VARCHAR(128) | 否 |  |  |
| permission_version | BIGINT UNSIGNED | 否 |  |  |
| export_status | VARCHAR(32) | 否 | 'PENDING' |  |
| object_file_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| expires_at | DATETIME(3) | 是 |  |  |
| error_code | VARCHAR(64) | 是 |  |  |

唯一键：tenant_id,requester_member_id,request_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,requester_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,report_id) → bi_report (tenant_id,id)
- (tenant_id,query_task_id) → bi_query_task (tenant_id,id)
- (tenant_id,object_file_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_report_subscription · 报告订阅

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| report_id | BIGINT UNSIGNED | 否 |  | bi_report |
| creator_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| schedule_expression | VARCHAR(128) | 否 |  |  |
| timezone | VARCHAR(128) | 否 |  |  |
| recipients_json | JSON | 否 |  |  |
| subscription_status | VARCHAR(32) | 否 | 'DISABLED' |  |
| next_run_at | DATETIME(3) | 是 |  |  |

唯一键：
查询索引：tenant_id,subscription_status,next_run_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,report_id) → bi_report (tenant_id,id)
- (tenant_id,creator_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_report_delivery · 按收件人报告投递

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| report_subscription_id | BIGINT UNSIGNED | 否 |  | bi_report_subscription |
| scheduled_window | DATETIME(3) | 否 |  |  |
| recipient_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| permission_version | BIGINT UNSIGNED | 否 |  |  |
| export_task_id | BIGINT UNSIGNED | 是 |  | bi_export_task |
| delivery_status | VARCHAR(32) | 否 | 'PENDING' |  |
| delivered_at | DATETIME(3) | 是 |  |  |
| error_code | VARCHAR(64) | 是 |  |  |

唯一键：tenant_id,report_subscription_id,scheduled_window,recipient_member_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,report_subscription_id) → bi_report_subscription (tenant_id,id)
- (tenant_id,recipient_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,export_task_id) → bi_export_task (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## bi_alert_rule · 告警规则

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| metric_version_id | BIGINT UNSIGNED | 是 |  | bi_metric_version |
| rule_name | VARCHAR(200) | 否 |  |  |
| condition_ast | JSON | 否 |  |  |
| owner_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| rule_status | VARCHAR(32) | 否 | 'DISABLED' |  |

唯一键：
查询索引：tenant_id,workspace_id,rule_status
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,metric_version_id) → bi_metric_version (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## bi_alert_event · 告警事件

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| alert_rule_id | BIGINT UNSIGNED | 否 |  | bi_alert_rule |
| dedupe_key | VARCHAR(128) | 否 |  |  |
| evidence_json | JSON | 否 |  |  |
| event_status | VARCHAR(32) | 否 | 'OPEN' |  |
| occurred_at | DATETIME(3) | 否 |  |  |
| resolved_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,dedupe_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,alert_rule_id) → bi_alert_rule (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_notification · 成员通知

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| recipient_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| dedupe_key | VARCHAR(128) | 否 |  |  |
| notification_type | VARCHAR(32) | 否 |  |  |
| title | VARCHAR(200) | 否 |  |  |
| content_summary | TEXT | 是 |  |  |
| asset_id | BIGINT UNSIGNED | 是 |  | sys_asset |
| read_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,recipient_member_id,dedupe_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,recipient_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,asset_id) → sys_asset (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_conversation · AI会话

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 否 |  | sys_workspace |
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| title | VARCHAR(200) | 否 |  |  |
| conversation_status | VARCHAR(32) | 否 | 'ACTIVE' |  |
| context_version | INT UNSIGNED | 否 | 0 |  |

唯一键：
查询索引：tenant_id,member_id,created_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_message · AI会话消息

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| conversation_id | BIGINT UNSIGNED | 否 |  | ai_conversation |
| message_seq | BIGINT UNSIGNED | 否 |  |  |
| message_role | VARCHAR(32) | 否 |  |  |
| content_redacted | TEXT | 是 |  |  |
| content_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |
| query_task_id | BIGINT UNSIGNED | 是 |  | bi_query_task |
| ai_task_id | BIGINT UNSIGNED | 是 |  | ai_task |

唯一键：tenant_id,conversation_id,message_seq
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,conversation_id) → ai_conversation (tenant_id,id)
- (tenant_id,content_object_id) → sys_object_file (tenant_id,id)
- (tenant_id,query_task_id) → bi_query_task (tenant_id,id)
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_task · AI工作流任务

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| conversation_id | BIGINT UNSIGNED | 否 |  | ai_conversation |
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| request_key | VARCHAR(128) | 否 |  |  |
| graph_thread_key | VARCHAR(255) | 否 |  |  |
| checkpoint_ref | VARCHAR(512) | 是 |  |  |
| permission_version | BIGINT UNSIGNED | 否 |  |  |
| task_status | VARCHAR(32) | 否 | 'ACCEPTED' |  |
| attempt | INT UNSIGNED | 否 | 1 |  |
| context_version | INT UNSIGNED | 否 | 0 |  |
| last_sequence | BIGINT UNSIGNED | 否 | 0 |  |
| plan_summary | TEXT | 是 |  |  |
| started_at | DATETIME(3) | 是 |  |  |
| finished_at | DATETIME(3) | 是 |  |  |
| deadline_at | DATETIME(3) | 否 |  |  |
| error_code | VARCHAR(64) | 是 |  |  |

唯一键：tenant_id,member_id,request_key；tenant_id,graph_thread_key
查询索引：tenant_id,task_status,deadline_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,conversation_id) → ai_conversation (tenant_id,id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_conversation_context · AI会话确认上下文版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| tenant_id | BIGINT UNSIGNED | 否 |  | sys_tenant |
| conversation_id | BIGINT UNSIGNED | 否 |  | ai_conversation |
| context_version | INT UNSIGNED | 否 |  |  |
| context_json | JSON | 否 |  |  |
| created_at | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) |  |

主键：tenant_id,conversation_id,context_version

外键：
- (tenant_id,conversation_id) → ai_conversation (tenant_id,id)

## ai_task_auth_snapshot · AI任务授权快照

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| tenant_id | BIGINT UNSIGNED | 否 |  | sys_tenant |
| ai_task_id | BIGINT UNSIGNED | 否 |  | ai_task |
| session_id | BIGINT UNSIGNED | 否 |  |  |
| user_id | BIGINT UNSIGNED | 否 |  |  |
| member_id | BIGINT UNSIGNED | 否 |  |  |
| tenant_permission_version | BIGINT UNSIGNED | 否 |  |  |
| member_permission_version | BIGINT UNSIGNED | 否 |  |  |
| workspace_id | BIGINT UNSIGNED | 是 |  |  |
| permission_code | VARCHAR(128) | 否 |  |  |
| asset_id | BIGINT UNSIGNED | 是 |  |  |
| action_code | VARCHAR(64) | 是 |  |  |
| created_at | DATETIME(3) | 否 | CURRENT_TIMESTAMP(3) |  |

主键：tenant_id,ai_task_id

外键：
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)

## ai_task_event · 可恢复AI任务事件

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| tenant_id | BIGINT UNSIGNED | 否 |  | sys_tenant |
| ai_task_id | BIGINT UNSIGNED | 否 |  | ai_task |
| sequence | BIGINT UNSIGNED | 否 |  |  |
| event_type | VARCHAR(32) | 否 |  |  |
| trace_id | VARCHAR(128) | 否 |  |  |
| payload_json | JSON | 否 |  |  |
| emitted_at | DATETIME(3) | 否 |  |  |
| retention_at | DATETIME(3) | 否 |  |  |

主键：tenant_id,ai_task_id,sequence
查询索引：retention_at

外键：
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)

## ai_tool_invocation · AI受控工具调用

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| ai_task_id | BIGINT UNSIGNED | 否 |  | ai_task |
| call_seq | INT UNSIGNED | 否 |  |  |
| tool_name | VARCHAR(64) | 否 |  |  |
| arguments_hash | CHAR(64) | 否 |  |  |
| query_task_id | BIGINT UNSIGNED | 是 |  | bi_query_task |
| result_summary | JSON | 是 |  |  |
| invocation_status | VARCHAR(32) | 否 |  |  |
| started_at | DATETIME(3) | 否 |  |  |
| finished_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,ai_task_id,call_seq
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)
- (tenant_id,query_task_id) → bi_query_task (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_model_config · 模型外发配置

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| config_name | VARCHAR(64) | 否 |  |  |
| provider_code | VARCHAR(64) | 否 |  |  |
| model_name | VARCHAR(128) | 否 |  |  |
| credential_ref | VARCHAR(255) | 是 |  |  |
| allowed_regions_json | JSON | 否 |  |  |
| data_policy_json | JSON | 否 |  |  |
| external_enabled | TINYINT UNSIGNED | 否 | 0 |  |
| config_status | VARCHAR(32) | 否 | 'DISABLED' |  |

唯一键：tenant_id,config_name
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_usage_ledger · AI供应商费用流水

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| ai_task_id | BIGINT UNSIGNED | 否 |  | ai_task |
| provider_code | VARCHAR(64) | 否 |  |  |
| provider_request_id | VARCHAR(128) | 否 |  |  |
| model_name | VARCHAR(128) | 否 |  |  |
| prompt_tokens | BIGINT UNSIGNED | 否 |  |  |
| completion_tokens | BIGINT UNSIGNED | 否 |  |  |
| amount_minor_units | BIGINT | 否 |  |  |
| currency | CHAR(3) | 否 |  |  |
| is_estimated | TINYINT UNSIGNED | 否 | 1 |  |
| occurred_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,provider_code,provider_request_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## ai_feedback · AI质量反馈

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| ai_task_id | BIGINT UNSIGNED | 否 |  | ai_task |
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| rating | TINYINT UNSIGNED | 否 |  |  |
| comment_redacted | TEXT | 是 |  |  |

唯一键：tenant_id,ai_task_id,member_id
查询索引：
CHECK：rating IN (0,1)

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,ai_task_id) → ai_task (tenant_id,id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_plan · 全局套餐

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| plan_code | VARCHAR(64) | 否 |  |  |
| plan_name | VARCHAR(200) | 否 |  |  |
| plan_status | VARCHAR(32) | 否 | 'DRAFT' |  |

唯一键：plan_code
查询索引：
CHECK：

外键：
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_plan_version · 套餐价格版本

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| plan_id | BIGINT UNSIGNED | 否 |  | saas_plan |
| version_no | INT UNSIGNED | 否 |  |  |
| price_minor_units | BIGINT | 否 |  |  |
| currency | CHAR(3) | 否 |  |  |
| billing_period | VARCHAR(32) | 否 |  |  |
| quota_json | JSON | 否 |  |  |
| version_status | VARCHAR(32) | 否 | 'DRAFT' |  |
| effective_from | DATETIME(3) | 是 |  |  |

唯一键：plan_id,version_no
查询索引：
CHECK：price_minor_units >= 0

外键：
- (plan_id) → saas_plan (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_subscription · 租户订阅

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| plan_version_id | BIGINT UNSIGNED | 否 |  | saas_plan_version |
| subscription_status | VARCHAR(32) | 否 | 'TRIAL' |  |
| period_start | DATETIME(3) | 否 |  |  |
| period_end | DATETIME(3) | 否 |  |  |
| cancel_at_period_end | TINYINT UNSIGNED | 否 | 0 |  |

唯一键：
查询索引：tenant_id,subscription_status,period_end
CHECK：period_end > period_start

外键：
- (tenant_id) → sys_tenant (id)
- (plan_version_id) → saas_plan_version (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_order · 订阅订单

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| subscription_id | BIGINT UNSIGNED | 否 |  | saas_subscription |
| order_no | VARCHAR(128) | 否 |  |  |
| plan_version_id | BIGINT UNSIGNED | 否 |  | saas_plan_version |
| amount_minor_units | BIGINT | 否 |  |  |
| currency | CHAR(3) | 否 |  |  |
| price_snapshot | JSON | 否 |  |  |
| order_status | VARCHAR(32) | 否 | 'PENDING' |  |
| paid_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,order_no
查询索引：
CHECK：amount_minor_units >= 0

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,subscription_id) → saas_subscription (tenant_id,id)
- (plan_version_id) → saas_plan_version (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_payment_event · 支付幂等事件

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| order_id | BIGINT UNSIGNED | 否 |  | saas_order |
| provider_code | VARCHAR(64) | 否 |  |  |
| provider_event_id | VARCHAR(128) | 否 |  |  |
| event_type | VARCHAR(32) | 否 |  |  |
| amount_minor_units | BIGINT | 否 |  |  |
| currency | CHAR(3) | 否 |  |  |
| verified_at | DATETIME(3) | 否 |  |  |
| payload_hash | CHAR(64) | 否 |  |  |
| processed_at | DATETIME(3) | 是 |  |  |

唯一键：provider_code,provider_event_id
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,order_id) → saas_order (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_quota_balance · 配额余额

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| quota_code | VARCHAR(64) | 否 |  |  |
| window_start | DATETIME(3) | 否 |  |  |
| window_end | DATETIME(3) | 否 |  |  |
| quota_limit | BIGINT UNSIGNED | 否 |  |  |
| used_amount | BIGINT UNSIGNED | 否 | 0 |  |
| reserved_amount | BIGINT UNSIGNED | 否 | 0 |  |

唯一键：tenant_id,quota_code,window_start
查询索引：
CHECK：used_amount + reserved_amount <= quota_limit；window_end > window_start

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## saas_quota_ledger · 配额幂等流水

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| quota_balance_id | BIGINT UNSIGNED | 否 |  | saas_quota_balance |
| business_event_key | VARCHAR(128) | 否 |  |  |
| reservation_key | VARCHAR(128) | 否 |  |  |
| event_type | VARCHAR(32) | 否 |  |  |
| amount | BIGINT UNSIGNED | 否 |  |  |
| occurred_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,business_event_key
查询索引：tenant_id,reservation_key
CHECK：event_type IN ('RESERVE','CONFIRM','RELEASE')；amount > 0

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,quota_balance_id) → saas_quota_balance (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_object_file · 对象文件元数据

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| workspace_id | BIGINT UNSIGNED | 是 |  | sys_workspace |
| owner_member_id | BIGINT UNSIGNED | 是 |  | sys_tenant_member |
| object_key | VARCHAR(512) | 否 |  |  |
| bucket_name | VARCHAR(128) | 否 |  |  |
| file_name | VARCHAR(255) | 否 |  |  |
| content_type | VARCHAR(128) | 否 |  |  |
| size_bytes | BIGINT UNSIGNED | 否 |  |  |
| content_hash | CHAR(64) | 否 |  |  |
| purpose | VARCHAR(32) | 否 |  |  |
| file_status | VARCHAR(32) | 否 | 'QUARANTINED' |  |
| expires_at | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,object_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,workspace_id) → sys_workspace (tenant_id,id)
- (tenant_id,owner_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
- (tenant_id,workspace_id,owner_member_id) → sys_workspace_member (tenant_id,workspace_id,member_id)

## sys_audit_event · 追加审计

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| actor_user_id | BIGINT UNSIGNED | 是 |  | sys_user |
| actor_member_id | BIGINT UNSIGNED | 是 |  | sys_tenant_member |
| action_code | VARCHAR(128) | 否 |  |  |
| resource_type | VARCHAR(32) | 否 |  |  |
| resource_id | BIGINT UNSIGNED | 是 |  |  |
| permission_version | BIGINT UNSIGNED | 是 |  |  |
| trace_id | VARCHAR(64) | 否 |  |  |
| result_code | VARCHAR(64) | 否 |  |  |
| summary_json | JSON | 是 |  |  |
| occurred_at | DATETIME(3) | 否 |  |  |

唯一键：
查询索引：tenant_id,occurred_at；tenant_id,trace_id
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (actor_user_id) → sys_user (id)
- (tenant_id,actor_member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_outbox_event · 事务消息箱

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| event_key | VARCHAR(128) | 否 |  |  |
| aggregate_type | VARCHAR(32) | 否 |  |  |
| aggregate_id | BIGINT UNSIGNED | 否 |  |  |
| event_type | VARCHAR(128) | 否 |  |  |
| schema_version | INT UNSIGNED | 否 | 1 |  |
| payload_json | JSON | 否 |  |  |
| event_status | VARCHAR(32) | 否 | 'PENDING' |  |
| retry_count | INT UNSIGNED | 否 | 0 |  |
| available_at | DATETIME(3) | 否 |  |  |
| published_at | DATETIME(3) | 是 |  |  |
| lease_owner | VARCHAR(128) | 是 |  |  |
| lease_until | DATETIME(3) | 是 |  |  |

唯一键：tenant_id,event_key
查询索引：event_status,available_at；tenant_id,aggregate_type,aggregate_id
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_inbox_dedup · 消费者幂等

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| consumer_code | VARCHAR(64) | 否 |  |  |
| event_key | VARCHAR(128) | 否 |  |  |
| processed_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,consumer_code,event_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_idempotency_request · 接口幂等

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| api_route | VARCHAR(128) | 否 |  |  |
| idempotency_key | VARCHAR(128) | 否 |  |  |
| request_hash | CHAR(64) | 否 |  |  |
| request_status | VARCHAR(32) | 否 | 'PENDING' |  |
| response_ref | JSON | 是 |  |  |
| expires_at | DATETIME(3) | 否 |  |  |

唯一键：tenant_id,member_id,api_route,idempotency_key
查询索引：
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,member_id) → sys_tenant_member (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_support_grant · 临时支持授权

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| approver_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| support_user_id | BIGINT UNSIGNED | 否 |  | sys_user |
| ticket_code | VARCHAR(128) | 否 |  |  |
| scope_json | JSON | 否 |  |  |
| expires_at | DATETIME(3) | 否 |  |  |
| revoked_at | DATETIME(3) | 是 |  |  |

唯一键：
查询索引：tenant_id,support_user_id,expires_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,approver_member_id) → sys_tenant_member (tenant_id,id)
- (support_user_id) → sys_user (id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)

## sys_tenant_exit · 租户退出与墓碑

| 字段 | 类型 | 可空 | 默认值 | 引用 |
|---|---|---|---|---|
| requester_member_id | BIGINT UNSIGNED | 否 |  | sys_tenant_member |
| exit_status | VARCHAR(32) | 否 | 'REQUESTED' |  |
| requested_at | DATETIME(3) | 否 |  |  |
| revocable_until | DATETIME(3) | 否 |  |  |
| online_deleted_at | DATETIME(3) | 是 |  |  |
| backup_expire_at | DATETIME(3) | 是 |  |  |
| tombstone_hash | CHAR(64) | 是 |  |  |
| export_object_id | BIGINT UNSIGNED | 是 |  | sys_object_file |

唯一键：
查询索引：tenant_id,exit_status,created_at
CHECK：

外键：
- (tenant_id) → sys_tenant (id)
- (tenant_id,requester_member_id) → sys_tenant_member (tenant_id,id)
- (tenant_id,export_object_id) → sys_object_file (tenant_id,id)
- (created_by) → sys_user (id)
- (updated_by) → sys_user (id)
