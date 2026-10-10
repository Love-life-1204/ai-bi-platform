param(
    [ValidateSet('MySQL','ClickHouse','All')][string]$Target = 'MySQL',
    [switch]$StartClickHouse,
    [switch]$Test
)
$ErrorActionPreference = 'Stop'
$infraPath = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path -LiteralPath (Join-Path $infraPath '.env'))) {
    Copy-Item -LiteralPath (Join-Path $infraPath '.env.example') -Destination (Join-Path $infraPath '.env')
    throw 'Fill infrastructure/.env before initializing the databases.'
}
python -c "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)"
if ($LASTEXITCODE -ne 0) {
    throw 'Activate the project Python 3.12 Conda environment before initialization.'
}
python -c "import pymysql" 2>$null
if ($LASTEXITCODE -ne 0) {
    python -m pip install -r (Join-Path $infraPath 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
}
if ($Target -in @('MySQL', 'All')) {
    python -B (Join-Path $PSScriptRoot 'db.py') migrate-mysql
    if ($LASTEXITCODE -ne 0) { throw 'MySQL migration failed.' }
    python -B (Join-Path $PSScriptRoot 'provision_mysql_roles.py')
    if ($LASTEXITCODE -ne 0) { throw 'MySQL runtime role provisioning failed.' }
    if ($Test) {
        python -B (Join-Path $infraPath 'tests/test_mysql.py')
        if ($LASTEXITCODE -ne 0) { throw 'MySQL regression failed.' }
    }
}
if ($Target -in @('ClickHouse', 'All')) {
    if ($StartClickHouse) {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required to start ClickHouse. An existing HTTP endpoint can also be used without -StartClickHouse.' }
        docker compose --env-file (Join-Path $infraPath '.env') -f (Join-Path $infraPath 'docker/compose.yaml') up -d --wait clickhouse
        if ($LASTEXITCODE -ne 0) { throw 'ClickHouse startup failed.' }
    }
    python -B (Join-Path $PSScriptRoot 'db.py') migrate-clickhouse
    if ($LASTEXITCODE -ne 0) { throw 'ClickHouse migration failed.' }
    if ($Test) {
        python -B (Join-Path $infraPath 'tests/test_clickhouse.py')
        if ($LASTEXITCODE -ne 0) { throw 'ClickHouse regression failed.' }
    }
}
