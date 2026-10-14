param(
    [string]$EvidenceDirectory,
    [string]$SourceDatabase,
    [string]$RestoreDatabase,
    [switch]$KeepRestoreDatabase
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$infraPath = Join-Path $projectPath 'infrastructure'
$envPath = Join-Path $infraPath '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'infrastructure/.env is required for a backup restore drill.'
}

$settings = @{}
Get-Content -LiteralPath $envPath | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $settings[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$sourceDatabase = if ($SourceDatabase) { $SourceDatabase } else { $settings.MYSQL_DATABASE }
if ($sourceDatabase -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') {
    throw 'MYSQL_DATABASE is missing or invalid.'
}
if (-not $RestoreDatabase) {
    $RestoreDatabase = 'aibi_restore_' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddHHmmss')
}
if ($RestoreDatabase -notmatch '^aibi_restore_[A-Za-z0-9_]{1,48}$') {
    throw 'RestoreDatabase must use the isolated aibi_restore_ prefix.'
}
if ($RestoreDatabase -eq $sourceDatabase) {
    throw 'Restore target must never equal the source database.'
}
if (-not $EvidenceDirectory) {
    $EvidenceDirectory = Join-Path $projectPath 'artifacts/p1/backup-restore'
}
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
$dumpPath = Join-Path $EvidenceDirectory ($sourceDatabase + '.sql')
$startedAt = [DateTimeOffset]::UtcNow

$env:MYSQL_PWD = $settings.MYSQL_PASSWORD
try {
    & mysqldump --host=$($settings.MYSQL_HOST) --port=$($settings.MYSQL_PORT) `
        --user=$($settings.MYSQL_USER) --single-transaction --routines --events `
        --set-gtid-purged=OFF --result-file=$dumpPath $sourceDatabase
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed.' }
    & mysql --host=$($settings.MYSQL_HOST) --port=$($settings.MYSQL_PORT) `
        --user=$($settings.MYSQL_USER) --execute="CREATE DATABASE $RestoreDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs"
    if ($LASTEXITCODE -ne 0) { throw 'Creating isolated restore database failed.' }
    & mysql --host=$($settings.MYSQL_HOST) --port=$($settings.MYSQL_PORT) `
        --user=$($settings.MYSQL_USER) --database=$RestoreDatabase --execute="source $($dumpPath.Replace('\','/'))"
    if ($LASTEXITCODE -ne 0) { throw 'Restoring the backup failed.' }
    $sourceCount = & mysql --batch --skip-column-names --host=$($settings.MYSQL_HOST) `
        --port=$($settings.MYSQL_PORT) --user=$($settings.MYSQL_USER) --database=$sourceDatabase `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
    $restoreCount = & mysql --batch --skip-column-names --host=$($settings.MYSQL_HOST) `
        --port=$($settings.MYSQL_PORT) --user=$($settings.MYSQL_USER) --database=$RestoreDatabase `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
    if ($sourceCount -ne $restoreCount) { throw 'Restored table count differs from source.' }
    $finishedAt = [DateTimeOffset]::UtcNow
    $dumpSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpPath).Hash.ToLowerInvariant()
    Remove-Item -LiteralPath $dumpPath
    $cleanedAt = $null
    if (-not $KeepRestoreDatabase) {
        & mysql --host=$($settings.MYSQL_HOST) --port=$($settings.MYSQL_PORT) `
            --user=$($settings.MYSQL_USER) --execute="DROP DATABASE $RestoreDatabase"
        if ($LASTEXITCODE -ne 0) { throw 'Restore database cleanup failed.' }
        $cleanedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $evidence = [ordered]@{
        status = 'PASS'
        sourceDatabase = $sourceDatabase
        restoreDatabase = $RestoreDatabase
        startedAt = $startedAt.ToString('o')
        finishedAt = $finishedAt.ToString('o')
        rtoSeconds = [math]::Round(($finishedAt - $startedAt).TotalSeconds)
        tableCount = [int]$restoreCount
        dumpSha256 = $dumpSha256
        dumpRetained = $false
        cleanupRequired = [bool]$KeepRestoreDatabase
        cleanedAt = $cleanedAt
    }
    $evidence | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (
        Join-Path $EvidenceDirectory 'restore-evidence.json')
    $evidence | ConvertTo-Json -Depth 4
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
