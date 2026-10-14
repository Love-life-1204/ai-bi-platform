# API 自动化测试报告

生成时间：2026-10-13T22:09:25+08:00（Asia/Shanghai）。
代码版本：`05ae2637a39f570bd685ab410b0eeacc70416c98 + 原有未提交代码 + 本轮修改（未创建提交）`。

环境：Windows 11 x64；JDK 21.0.9 / Maven 3.9.12 / Node 24.15.0 / npm 8.19.4；AI Python 3.12.14，基础设施 Python 3.10.11。
隔离方式：Java test profile 使用内存持久层；浏览器使用本轮测试服务或明确标注的模拟 API；数据库写入仅限随机新建临时库。
原有工作区已含大量修改和未跟踪业务文件；没有 reset、覆盖原有修改或提交整个工作区。

统计：真实 HTTP 20 场景：14 通过、6 失败；其余接口八维未完整执行；ID 精度额外 1 失败不计入 HTTP 数量。不同报告会引用同一用例，不可横向相加。

## 结论：API 安全验收失败

实际启动可运行 JAR，通过回环随机端口发真实 HTTP；持久层为内存。注册、重复注册、登录/错误密码、校验、会话退出与失效有业务响应断言。

OpenAPI 共 172 个方法/路径；本轮额外真实 HTTP 20 场景，不覆盖每接口全部八维。MockMvc 是额外模块证据，不把它们计作真实数据库集成。

| 场景 | 实际 HTTP | 业务码 | 结果 |
|---|---:|---|---|
| health_envelope | 200 | SUCCESS | PASS |
| register | 201 | SUCCESS | PASS |
| register_duplicate | 409 | CONFLICT | PASS |
| login_bad_password | 401 | UNAUTHORIZED | PASS |
| login | 200 | SUCCESS | PASS |
| register_validation | 400 | INVALID_ARGUMENT | PASS |
| anonymous_memberships | 400 | INVALID_ARGUMENT | PASS |
| authenticated_memberships | 200 | SUCCESS | PASS |
| SEC_anonymous_tenant_read | 200 | SUCCESS | FAIL |
| SEC_anonymous_tenant_update | 200 | SUCCESS | FAIL |
| SEC_anonymous_member_add | 201 | SUCCESS | FAIL |
| switch_tenant_global | 200 | SUCCESS | PASS |
| create_test_workspace | 201 | SUCCESS | PASS |
| SEC_cross_tenant_header | 403 | FORBIDDEN | PASS |
| SEC_cross_tenant_path_read | 200 | SUCCESS | FAIL |
| SEC_roleless_datasource_create | 201 | SUCCESS | FAIL |
| tenant_header_with_global_token | 401 | UNAUTHORIZED | PASS |
| SEC_anonymous_tenant_freeze | 200 | SUCCESS | FAIL |
| logout | 204 | None | PASS |
| revoked_session | 401 | UNAUTHORIZED | PASS |

6 项鉴权失败均保留正确的 401/403 预期，没有改成接受 200/201。所有请求具有 trace_id，可在结果 JSON 与服务日志关联。接口详情及未覆盖项见 [API覆盖矩阵](API覆盖矩阵.md)。

## 执行命令和证据

以下命令路径相对于项目根目录；cwd 列指明执行目录。每步 JSON 含北京时间、退出码、耗时及完整命令。

| 步骤 | 开始时间(UTC+8) | 秒 | cwd | 命令 | 退出码 | 证据 |
|---|---|---:|---|---|---:|---|
| backend-verify-final | 2026-10-13T21:55:00.209962+08:00 | 40.25 | backend | `D:\Maven\bin\mvn.cmd --batch-mode --no-transfer-progress verify` | 0 | [日志](evidence/backend-verify-final.log) / [元数据](evidence/backend-verify-final.json) |
| live-api-browser-verified | 2026-10-13T21:59:26.952990+08:00 | 23.69 | . | `python testing/scripts/live_api_acceptance.py` | 1 | [日志](evidence/live-api-browser-verified.log) / [元数据](evidence/live-api-browser-verified.json) |

基线证据：[Git 状态](evidence/git-status-before.txt)、[原有跟踪文件差异](evidence/tracked-before.patch)、[源码 SHA-256](evidence/source-before.json)。
未执行项仅表示缺少证据，不算通过。结果只覆盖本轮实际运行的环境和数据规模。
