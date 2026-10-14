"""启动仅绑定回环的 test-profile JVM，验证真实 HTTP；结果不等于真库全链路。"""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "testing/reports/evidence"

def main():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    base = f"http://127.0.0.1:{port}"
    records = []
    def call(method, path, payload=None, token=None, tenant=None):
        headers = {"Content-Type":"application/json", "X-Trace-Id":"qa-"+uuid.uuid4().hex}
        if token:
            headers["Authorization"] = "Bearer " + token
        if tenant:
            headers["X-Tenant-Id"] = str(tenant)
        request = urllib.request.Request(base+path, data=json.dumps(payload).encode() if payload is not None else None,
                                         headers=headers, method=method)
        try:
            response = urllib.request.urlopen(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            return response.status, json.loads(body) if body else {}, response.headers.get("X-Trace-Id")
    def check(name, method, path, expected, payload=None, token=None, tenant=None, predicate=None):
        code, result, trace = call(method, path, payload, token, tenant)
        passed = code in expected and (predicate(result) if predicate else True)
        records.append(dict(name=name, method=method, path=path, expected_status=expected,
            actual_status=code, business_code=result.get("code"), trace_id=trace,
            status="PASS" if passed else "FAIL"))
        return result.get("data", {})
    log = (OUT / "live-api-server.log").open("w", encoding="utf-8")
    command = [r"D:\Java\bin\java.exe", "-jar", str(ROOT / "backend/target/ai-bi-backend-0.1.0-SNAPSHOT.jar"),
               "--spring.profiles.active=test", "--server.address=127.0.0.1", f"--server.port={port}",
               "--app.ingestion.storage-dir=" + str(OUT / "http-imports")]
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
    try:
        for _ in range(120):
            try:
                if call("GET", "/api/system/status")[0] == 200:
                    break
            except (OSError, urllib.error.URLError):
                pass
            if process.poll() is not None:
                raise RuntimeError("隔离后端启动失败，请查看 live-api-server.log")
            time.sleep(.5)
        else:
            raise RuntimeError("隔离后端启动超时")
        check("health_envelope", "GET", "/api/system/status", [200], predicate=lambda r: r.get("code") == "SUCCESS" and bool(r.get("trace_id")))
        _, spec, _ = call("GET", "/v3/api-docs")
        (OUT / "openapi.json").write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding="utf-8")
        email = "qa-" + uuid.uuid4().hex + "@example.invalid"
        password = "qa-only-strong-password-2026"
        user = check("register", "POST", "/api/auth/register", [201], {"email":email,"displayName":"QA 用户","password":password}, predicate=lambda r:r.get("code")=="SUCCESS")
        check("register_duplicate", "POST", "/api/auth/register", [409], {"email":email,"displayName":"QA 用户","password":password})
        check("login_bad_password", "POST", "/api/auth/login", [401], {"email":email,"password":"wrong-password"})
        auth = check("login", "POST", "/api/auth/login", [200], {"email":email,"password":password}, predicate=lambda r:bool(r.get("data",{}).get("accessToken")))
        token = auth["accessToken"]
        check("register_validation", "POST", "/api/auth/register", [400], {"email":"bad","displayName":"","password":"x"})
        check("anonymous_memberships", "GET", "/api/users/me/memberships", [400,401,403])
        check("authenticated_memberships", "GET", "/api/users/me/memberships", [200], token=token, predicate=lambda r:r.get("data")==[])
        tenants = []
        for index in range(2):
            status, result, _ = call("POST", "/api/tenants", {"tenantCode":"qa_"+uuid.uuid4().hex,"tenantName":f"独立 QA 企业 {index}"})
            assert status == 201, result
            tenants.append(result["data"])
        ta, tb = [x["id"] for x in tenants]
        records.append(dict(name="DATA_browser_id_precision", status="PASS" if all(
            int(float(value)) == value for value in (user["id"], ta, tb)) else "FAIL",
            expected="JavaScript JSON.parse 保留业务 ID", actual="检查返回数字经 IEEE-754 转换是否保持原值"))
        check("SEC_anonymous_tenant_read", "GET", f"/api/tenants/{ta}", [401,403])
        check("SEC_anonymous_tenant_update", "PUT", f"/api/tenants/{ta}", [401,403], {"tenantName":"未授权改名","timezone":"Asia/Shanghai","defaultCurrency":"CNY","lockVersion":0})
        check("SEC_anonymous_member_add", "POST", f"/api/tenants/{ta}/members", [401,403], {"userId":user["id"],"memberName":"未授权加入"})
        switched = check("switch_tenant_global", "POST", "/api/auth/switch-tenant", [200], {"tenantId":ta}, token=token)
        tenant_token = switched["tenantAccessToken"]
        workspace = check("create_test_workspace", "POST", f"/api/tenants/{ta}/workspaces", [201],
            {"workspaceCode":"qa_workspace","workspaceName":"QA 工作空间"}, token=tenant_token)
        check("SEC_cross_tenant_header", "GET", "/api/datasources", [403], token=tenant_token, tenant=tb)
        check("SEC_cross_tenant_path_read", "GET", f"/api/tenants/{tb}", [401,403], token=tenant_token)
        check("SEC_roleless_datasource_create", "POST", "/api/datasources", [403], {
            "workspaceId":workspace["id"], "code":"qa_source", "name":"无角色数据源", "type":"MYSQL", "endpoint":{"host":"db.example.invalid","port":3306,"database":"sales","tls":True},
            "credential":{"username":"qa_readonly","password":"qa-only-fake-password"}}, token=tenant_token, tenant=ta)
        check("tenant_header_with_global_token", "POST", "/api/auth/switch-tenant", [401,403], {"tenantId":ta}, token=token, tenant=ta)
        check("SEC_anonymous_tenant_freeze", "POST", f"/api/tenants/{tb}/freeze", [401,403], {"lockVersion":0})
        browser_env = dict(os.environ, QA_API_BASE_URL=base, QA_EMAIL=email, QA_PASSWORD=password)
        with (OUT / "live-browser.log").open("w", encoding="utf-8") as browser_log:
            subprocess.run(["node", str(ROOT / "testing/scripts/browser_live.cjs")], env=browser_env,
                cwd=ROOT, stdout=browser_log, stderr=subprocess.STDOUT, timeout=90)
        check("logout", "POST", "/api/auth/logout", [204], token=token)
        check("revoked_session", "GET", "/api/users/me/memberships", [401], token=token)
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
        log.close()
        report = dict(profile="test", persistence="in-memory", records=records,
            passed=sum(x["status"]=="PASS" for x in records), failed=sum(x["status"]=="FAIL" for x in records))
        (OUT / "live-api-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False, indent=2))
    return int(report["failed"] > 0)

if __name__ == "__main__":
    raise SystemExit(main())
