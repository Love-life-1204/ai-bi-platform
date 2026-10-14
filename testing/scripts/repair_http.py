"""真实HTTP安全与入驻验收；只启动本轮回环test JVM，不使用业务数据库。"""
import argparse
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'testing/reports/evidence/autonomous-20261014'
sys.stdout.reconfigure(encoding='utf-8',errors='replace')


def main(label):
    OUT.mkdir(parents=True,exist_ok=True)
    jar=ROOT/'backend/target/ai-bi-backend-0.1.0-SNAPSHOT.jar'
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    base=f'http://127.0.0.1:{port}';records=[]
    def call(method,path,body=None,token=None,key=None):
        headers={'Content-Type':'application/json','X-Trace-Id':'repair-'+uuid.uuid4().hex}
        if token:headers['Authorization']='Bearer '+token
        if key:headers['Idempotency-Key']=key
        request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,
                                       headers=headers,method=method)
        try:response=urllib.request.urlopen(request,timeout=15)
        except urllib.error.HTTPError as exc:response=exc
        with response:
            raw=response.read();return response.status,json.loads(raw) if raw else {}
    def check(name,method,path,status,body=None,token=None,key=None,predicate=None):
        code,result=call(method,path,body,token,key)
        good=code in status and (predicate(result) if predicate else True)
        records.append({'name':name,'method':method,'path':path,'expected':status,
                        'actual':code,'business_code':result.get('code'),'trace_id':result.get('trace_id'),
                        'status':'PASS' if good else 'FAIL'})
        return result.get('data',{})
    log=(OUT/(label+'-server.log')).open('wb')
    command=[r'D:\Java\bin\java.exe','-jar',str(jar),'--spring.profiles.active=test',
             '--server.address=127.0.0.1',f'--server.port={port}','--app.datasource.enabled=false',
             '--app.messaging.enabled=false','--app.ingestion.storage-dir='+str(OUT/(label+'-imports'))]
    process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    try:
        for _ in range(100):
            try:
                if call('GET','/api/system/status')[0]==200:break
            except (OSError,urllib.error.URLError):pass
            if process.poll() is not None:raise RuntimeError('Isolated JVM startup failed')
            time.sleep(.25)
        else:raise RuntimeError('Isolated JVM startup timeout')
        password='repair-only-test-password-2026'
        email=uuid.uuid4().hex+'@example.invalid'
        user=check('register','POST','/api/auth/register',[201],{'email':email,'displayName':'隔离所有者','password':password})
        global_token=check('login','POST','/api/auth/login',[200],{'email':email,'password':password})['accessToken']
        body={'tenantCode':'repair_'+uuid.uuid4().hex,'tenantName':'隔离企业'}
        check('anonymous_create','POST','/api/tenants',[401],body)
        key='onboard-'+uuid.uuid4().hex
        tenant=check('owner_onboarding','POST','/api/tenants',[201],body,global_token,key)
        tid=tenant['id']
        check('same_key_replay','POST','/api/tenants',[201],body,global_token,key,
              lambda value:value.get('data',{}).get('id')==tid)
        changed={**body,'tenantName':'不同内容'}
        check('same_key_changed_body','POST','/api/tenants',[409],changed,global_token,key)
        check('owner_membership','GET','/api/users/me/memberships',[200],token=global_token,
              predicate=lambda value:len(value.get('data',[]))==1 and value['data'][0]['owner'])
        check('global_own_summary','GET','/api/users/me/tenants',[200],token=global_token,
              predicate=lambda value:len(value.get('data',[]))==1 and value['data'][0]['id']==tid)
        tenant_token=check('switch','POST','/api/auth/switch-tenant',[200],{'tenantId':tid},global_token)['tenantAccessToken']
        check('authorized_read','GET',f'/api/tenants/{tid}',[200],token=tenant_token)
        check('anonymous_read','GET',f'/api/tenants/{tid}',[401])
        check('anonymous_alias_read','GET',f'/api/v1/tenants/{tid}',[401])
        check('anonymous_update','PUT',f'/api/tenants/{tid}',[401],
              {'tenantName':'攻击','timezone':'UTC','defaultCurrency':'CNY','lockVersion':0})
        attacker_email=uuid.uuid4().hex+'@example.invalid'
        attacker_user=check('register_attacker','POST','/api/auth/register',[201],{'email':attacker_email,'displayName':'隔离攻击用户','password':password})
        attacker_global=check('attacker_login','POST','/api/auth/login',[200],{'email':attacker_email,'password':password})['accessToken']
        check('anonymous_member_add','POST',f'/api/tenants/{tid}/members',[401],{'userId':attacker_user['id'],'memberName':'攻击'})
        check('attacker_switch_target_denied','POST','/api/auth/switch-tenant',[403],{'tenantId':tid},attacker_global)
        check('attacker_membership_unchanged','GET','/api/users/me/memberships',[200],token=attacker_global,
              predicate=lambda value:value.get('data')==[])
        b=check('second_owner_onboarding','POST','/api/tenants',[201],
                {'tenantCode':'repair_b_'+uuid.uuid4().hex,'tenantName':'异租户'},attacker_global)
        btoken=check('second_owner_switch','POST','/api/auth/switch-tenant',[200],{'tenantId':b['id']},attacker_global)['tenantAccessToken']
        check('cross_tenant_read','GET',f'/api/tenants/{tid}',[403],token=btoken)
        check('cross_tenant_update','PUT',f'/api/tenants/{tid}',[403],
              {'tenantName':'攻击','timezone':'UTC','defaultCurrency':'CNY','lockVersion':0},btoken)
        check('cross_tenant_freeze','POST',f'/api/tenants/{tid}/freeze',[403],{'lockVersion':0},btoken)
        check('anonymous_freeze','POST',f'/api/tenants/{tid}/freeze',[401],{'lockVersion':0})
        check('owner_freeze','POST',f'/api/tenants/{tid}/freeze',[200],{'lockVersion':0},tenant_token)
        check('frozen_old_token','GET',f'/api/tenants/{tid}',[403],token=tenant_token)
        check('owner_recovery','POST',f'/api/tenants/{tid}/unfreeze',[200],{'lockVersion':1},global_token)
        check('unfreeze_does_not_revive','GET',f'/api/tenants/{tid}',[403],token=tenant_token)
        check('logout','POST','/api/auth/logout',[204],token=global_token)
        check('revoked_session','GET','/api/users/me/tenants',[401],token=global_token)
    finally:
        process.terminate()
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:process.kill();process.wait()
        log.close()
        result={'profile':'test','persistence':'in-memory','real_http':True,
                'artifact_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'command':command,'records':records,
                'passed':sum(r['status']=='PASS' for r in records),'failed':sum(r['status']=='FAIL' for r in records)}
        (OUT/(label+'-results.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps({'label':label,'passed':result['passed'],'failed':result['failed']},ensure_ascii=False))
    return int(result['failed']>0)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--label',required=True)
    raise SystemExit(main(parser.parse_args().label))
