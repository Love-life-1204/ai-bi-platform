"""把已登记隔离库凭据仅传入子进程环境，禁止默认连业务库。"""
import json
import argparse
import os
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'infrastructure/scripts'))
sys.path.insert(0,str(ROOT/'testing/scripts'))
import db
import repair_database
import repair_run

parser=argparse.ArgumentParser()
parser.add_argument('--label',default='p0-jdbc-onboarding')
parser.add_argument('--test', choices=['TenantOnboardingJdbcIT', 'FileScopeJdbcIT', 'IngestionAuthorizationJdbcIT',
                    'TenantOnboardingJdbcIT,FileScopeJdbcIT',
                    'TenantOnboardingJdbcIT,FileScopeJdbcIT,IngestionAuthorizationJdbcIT'], default='TenantOnboardingJdbcIT')
args=parser.parse_args()

name=json.loads((repair_run.RUN/'jdbc-isolation.json').read_text(encoding='utf-8'))['database']
with db.connect(False) as conn:repair_database.require_owned(name,conn)
config=db.config()
os.environ['AIBI_REPAIR_JDBC_URL']='jdbc:mysql://'+config.get('MYSQL_HOST','127.0.0.1')+':'+config.get('MYSQL_PORT','3306')+'/'+name+'?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=DISABLED'
os.environ['AIBI_REPAIR_JDBC_USER']=config.get('MYSQL_USER','root')
os.environ['AIBI_REPAIR_JDBC_PASSWORD']=config.get('MYSQL_PASSWORD','')
raise SystemExit(repair_run.run(args.label,'backend',[
    r'D:\Maven\bin\mvn.cmd','--offline','--batch-mode','--no-transfer-progress',
    '-Dtest='+args.test,'-DargLine=-javaagent:D:/Maven/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar','test']))
