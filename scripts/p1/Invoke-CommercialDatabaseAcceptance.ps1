param()
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envPath = Join-Path $projectPath 'infrastructure/.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'infrastructure/.env is required for database acceptance.'
}
$settings = @{}
Get-Content -LiteralPath $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $settings[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$testDatabase = 'aibi_p1_acceptance_' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddHHmmss')
if ($testDatabase -notmatch '^aibi_p1_acceptance_[0-9]{14}$') {
    throw 'Unexpected acceptance database name.'
}
$env:MYSQL_HOST = $settings.MYSQL_HOST
$env:MYSQL_PORT = $settings.MYSQL_PORT
$env:MYSQL_USER = $settings.MYSQL_USER
$env:MYSQL_PASSWORD = $settings.MYSQL_PASSWORD
$env:MYSQL_DATABASE = $testDatabase
try {
    Push-Location $projectPath
    try {
        & conda run --no-capture-output -n AI_BI_Platform python -B `
            infrastructure/scripts/db.py migrate-mysql
        if ($LASTEXITCODE -ne 0) { throw 'Fresh commercial migration failed.' }
        & conda run --no-capture-output -n AI_BI_Platform python -B `
            infrastructure/tests/test_mysql.py
        if ($LASTEXITCODE -ne 0) { throw 'Commercial database regression failed.' }
    } finally {
        Pop-Location
    }
} finally {
    $env:MYSQL_PWD = $settings.MYSQL_PASSWORD
    try {
        & mysql --host=$($settings.MYSQL_HOST) --port=$($settings.MYSQL_PORT) `
            --user=$($settings.MYSQL_USER) --execute="DROP DATABASE IF EXISTS $testDatabase"
        if ($LASTEXITCODE -ne 0) { throw 'Acceptance database cleanup failed.' }
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        Remove-Item Env:MYSQL_DATABASE -ErrorAction SilentlyContinue
    }
}
