"""创建不含登录密码的运行角色；只授权指定库，不改变 root 或其他现有账户。"""
from db import connect, config, mysql_tables

APPEND_ONLY = {'sys_audit_event','ai_usage_ledger','saas_quota_ledger','saas_payment_event',
               'bi_dataset_version','bi_metric_version','bi_chart_version','bi_dashboard_version','saas_plan_version'}
WORKER_WRITE = {'bi_sync_run','bi_batch','bi_quality_result','bi_sync_watermark',
                'bi_dataset_publication','bi_import_job','bi_query_task','bi_export_task',
                'bi_report_delivery','sys_object_file','sys_outbox_event','sys_inbox_dedup'}


def main():
    database = config().get('MYSQL_DATABASE', 'ai_bi_core')
    # connect 已验证数据库标识，角色名固定且仅增加明确表级权限。
    with connect() as conn, conn.cursor() as cur:
        cur.execute("CREATE ROLE IF NOT EXISTS 'aibi_api_role', 'aibi_worker_role'")
        for table in mysql_tables():
            api = 'SELECT, INSERT' if table in APPEND_ONLY else 'SELECT, INSERT, UPDATE, DELETE'
            if table in {'sys_permission','saas_plan','saas_plan_version'}:
                api = 'SELECT'
            cur.execute(f"GRANT {api} ON `{database}`.`{table}` TO 'aibi_api_role'")
            # Worker 不读取密码、会话、支付、用户或 AI 内容表。
            if table in WORKER_WRITE:
                cur.execute(f"GRANT SELECT, INSERT, UPDATE ON `{database}`.`{table}` TO 'aibi_worker_role'")
            elif table in {'sys_tenant','sys_tenant_member','sys_workspace','bi_dataset','bi_dataset_version','bi_dataset_field','bi_datasource','bi_source_object','bi_source_column','bi_sync_job','bi_quality_rule'}:
                cur.execute(f"GRANT SELECT ON `{database}`.`{table}` TO 'aibi_worker_role'")
        cur.execute(f"GRANT SELECT, INSERT ON `{database}`.`sys_audit_event` TO 'aibi_worker_role'")
    print('Created aibi_api_role and aibi_worker_role with table-level grants; no login users or passwords created.')


if __name__ == '__main__':
    main()
