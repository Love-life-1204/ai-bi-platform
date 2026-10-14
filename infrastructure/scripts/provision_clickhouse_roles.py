"""创建无登录密码的 ClickHouse 最小权限角色；用户与密钥由密钥管理流程完成。"""
from db import clickhouse


def main():
    clickhouse('CREATE ROLE IF NOT EXISTS aibi_query_role')
    # API 只读已治理的明细层和汇总层，不授予 RAW、DDL 或系统表权限。
    clickhouse('GRANT SELECT ON ai_bi_dwd.* TO aibi_query_role')
    clickhouse('GRANT SELECT ON ai_bi_dws.* TO aibi_query_role')
    clickhouse('CREATE ROLE IF NOT EXISTS aibi_ingestion_role')
    clickhouse('GRANT INSERT ON ai_bi_raw.ingest_event TO aibi_ingestion_role')
    clickhouse('GRANT INSERT ON ai_bi_ops.batch_ingest_receipt TO aibi_ingestion_role')
    print('Created query and ingestion roles with separated read/write privileges; no login user created.')


if __name__ == '__main__':
    main()
