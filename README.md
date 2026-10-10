# AI BI 智能数据分析平台

数据库底座已按项目设计实现，应用代码尚未初始化。

- [数据库配置、启动与验证说明](infrastructure/README.md)
- [MySQL 最终初始化 SQL](infrastructure/sql/mysql.sql)
- [ClickHouse 最终初始化 SQL](infrastructure/sql/clickhouse.sql)
- [MySQL 字段与关系字典](docs/mysql_data_dictionary.md)

```powershell
./infrastructure/scripts/Initialize-Database.ps1 -Target MySQL -Test
```

本地连接配置放在 infrastructure/.env；不要提交数据库密码。
