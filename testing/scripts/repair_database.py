"""仅只读备份源库；DDL/DML只允许本工具随机创建并登记的隔离库。"""
import argparse
import base64
import ctypes
import datetime as dt
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'infrastructure/scripts'))
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
import db

PRIVATE = ROOT/'.repair-private/autonomous-20261014'
OUT = ROOT/'testing/reports/evidence/autonomous-20261014'
PATTERN = re.compile(r'aibi_repair_[a-f0-9]{16}\Z')


class Blob(ctypes.Structure):
    _fields_ = [('size', ctypes.c_ulong), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def protect(data, decrypt=False):
    if os.name != 'nt':
        raise RuntimeError('This backup uses Windows current-user DPAPI')
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = Blob()
    fn = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    if not fn(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        kernel.LocalFree(target.data)


def pack(value):
    if isinstance(value, bytes): return {'__type__':'bytes','value':base64.b64encode(value).decode()}
    if isinstance(value, Decimal): return {'__type__':'decimal','value':str(value)}
    if isinstance(value, (dt.datetime, dt.date, dt.time)):
        return {'__type__':type(value).__name__,'value':value.isoformat()}
    return value


def unpack(value):
    if isinstance(value, dict) and '__type__' in value:
        code, raw = value['__type__'], value['value']
        if code=='bytes': return base64.b64decode(raw)
        if code=='decimal': return Decimal(raw)
        return {'datetime':dt.datetime,'date':dt.date,'time':dt.time}[code].fromisoformat(raw)
    return value


def encoded(rows):
    return json.dumps([[pack(v) for v in row] for row in rows], ensure_ascii=False,
                      separators=(',', ':'), allow_nan=False).encode('utf-8')


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, default=str)+'\n',encoding='utf-8')


def identity(conn):
    with conn.cursor() as cur:
        cur.execute('SELECT @@hostname,@@port,VERSION()')
        return list(cur.fetchone())


def register_database(conn):
    PRIVATE.mkdir(parents=True, exist_ok=True)
    name='aibi_repair_'+secrets.token_hex(8)
    with conn.cursor() as cur:
        cur.execute(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs')
    record={'database':name,'server':identity(conn),'created_at':dt.datetime.now(dt.timezone.utc).isoformat()}
    save(PRIVATE/(name+'.owned.json'),record)
    return name


def require_owned(name, conn):
    path=PRIVATE/(name+'.owned.json')
    if not PATTERN.fullmatch(name) or not path.exists():
        raise RuntimeError('Refusing unowned or non-isolated database')
    if json.loads(path.read_text(encoding='utf-8'))['server'] != identity(conn):
        raise RuntimeError('Database server identity changed')


def backup():
    import pymysql
    folder=PRIVATE/'backup'
    if (folder/'manifest.json').exists():
        raise RuntimeError('Backup already retained; do not overwrite')
    folder.mkdir(parents=True,exist_ok=True)
    manifest={'started_at':dt.datetime.now(dt.timezone.utc).isoformat(),'protection':'Windows DPAPI current user',
              'tables':{}}
    begin=time.monotonic()
    with db.connect() as conn:
        manifest['server']=identity(conn)
        with conn.cursor() as cur:
            cur.execute('SELECT DATABASE()');manifest['source_database']=cur.fetchone()[0]
            cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")
            tables=[r[0] for r in cur.fetchall()]
            for table in tables:
                if not re.fullmatch(r'[A-Za-z0-9_]+',table):raise RuntimeError('Unsafe source table')
                cur.execute(f'SHOW CREATE TABLE `{table}`');ddl=cur.fetchone()[1]
                cur.execute('SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=%s AND index_name=\'PRIMARY\' ORDER BY seq_in_index',(table,))
                order=[r[0] for r in cur.fetchall()]
                order_sql=' ORDER BY '+','.join('`'+c+'`' for c in order) if order else ''
                digest=hashlib.sha256();count=0;files=[]
                with conn.cursor(pymysql.cursors.SSCursor) as stream:
                    stream.execute(f'SELECT * FROM `{table}`'+order_sql)
                    columns=[c[0] for c in stream.description]
                    while True:
                        rows=stream.fetchmany(500)
                        if not rows:break
                        raw=encoded(rows);digest.update(raw);count+=len(rows)
                        dest=folder/(table+'-'+str(len(files)).zfill(6)+'.dpapi')
                        sealed=protect(raw)
                        assert protect(sealed,True)==raw
                        dest.write_bytes(sealed)
                        files.append({'file':dest.name,'sha256':hashlib.sha256(sealed).hexdigest()})
                manifest['tables'][table]={'ddl':ddl,'columns':columns,'order':order,
                    'rows':count,'data_sha256':digest.hexdigest(),'chunks':files}
            try:
                cur.execute('SHOW MASTER STATUS');manifest['binlog_coordinate']=cur.fetchone()
            except Exception as exc:manifest['binlog_coordinate_unavailable']=type(exc).__name__
        conn.rollback()
    manifest['finished_at']=dt.datetime.now(dt.timezone.utc).isoformat()
    manifest['seconds']=round(time.monotonic()-begin,3)
    save(folder/'manifest.json',manifest)
    save(OUT/'backup-created.json',{'source_database':manifest['source_database'],'tables':len(tables),
         'rows':sum(t['rows'] for t in manifest['tables'].values()),'seconds':manifest['seconds'],
         'protection':manifest['protection'],'retained_backup':str(folder),'restore_verified':False})
    print('Retained encrypted read-only backup:',len(tables),'tables')


def clone():
    manifest=json.loads((PRIVATE/'backup/manifest.json').read_text(encoding='utf-8'))
    begin=time.monotonic();constraints=[]
    with db.connect(False) as conn:
        if identity(conn)!=manifest['server']:raise RuntimeError('Source server identity changed')
        name=register_database(conn)
        with conn.cursor() as cur:
            cur.execute(f'USE `{name}`')
            for table,info in manifest['tables'].items():
                lines=[]
                for line in info['ddl'].splitlines():
                    if re.match(r'\s*CONSTRAINT .* FOREIGN KEY ',line):
                        constraints.append(f'ALTER TABLE `{table}` ADD '+line.strip().rstrip(','))
                    else:lines.append(line)
                ddl=re.sub(r',\s*\n\)', '\n)', '\n'.join(lines))
                cur.execute(ddl)
            for table,info in manifest['tables'].items():
                columns=','.join('`'+c+'`' for c in info['columns'])
                marks=','.join(['%s']*len(info['columns']))
                for chunk in info['chunks']:
                    sealed=(PRIVATE/'backup'/chunk['file']).read_bytes()
                    if hashlib.sha256(sealed).hexdigest()!=chunk['sha256']:raise RuntimeError('Backup checksum mismatch')
                    rows=[[unpack(v) for v in row] for row in json.loads(protect(sealed,True))]
                    cur.executemany(f'INSERT INTO `{table}` ({columns}) VALUES ({marks})',rows)
            for ddl in constraints:cur.execute(ddl)
            checked=0
            for table,info in manifest['tables'].items():
                order=' ORDER BY '+','.join('`'+c+'`' for c in info['order']) if info['order'] else ''
                cur.execute(f'SELECT * FROM `{table}`'+order)
                digest=hashlib.sha256();rows=0
                while True:
                    chunk=cur.fetchmany(500)
                    if not chunk:break
                    digest.update(encoded(chunk));rows+=len(chunk)
                if rows!=info['rows'] or digest.hexdigest()!=info['data_sha256']:
                    raise RuntimeError('Restored data mismatch: '+table)
                checked+=1
    report={'database':name,'tables_checked':checked,'foreign_keys_restored':len(constraints),
            'all_table_data_hashes_match':True,'seconds':round(time.monotonic()-begin,3),
            'application_started_against_clone':False,'contains_unsanitized_backup_data':True,
            'restriction':'Restore-verification only; no external integrations or application access'}
    save(OUT/'backup-restore-verified.json',report)
    print(json.dumps(report,ensure_ascii=False))


def new_schema():
    with db.connect(False) as conn:name=register_database(conn)
    old=os.environ.get('MYSQL_DATABASE');os.environ['MYSQL_DATABASE']=name
    try:db.migrate_mysql()
    finally:
        if old is None:os.environ.pop('MYSQL_DATABASE',None)
        else:os.environ['MYSQL_DATABASE']=old
    save(OUT/'jdbc-isolation.json',{'database':name,'created_by_this_run':True,'source':'new_current_schema'})
    print('Isolated new schema:',name)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['backup','clone','new-schema'])
    action=parser.parse_args().action
    {'backup':backup,'clone':clone,'new-schema':new_schema}[action]()
