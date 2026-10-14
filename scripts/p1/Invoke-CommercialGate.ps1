param(
    [switch]$IncludeInfrastructure,
    [switch]$IncludeFrontend,
    [switch]$IncludeBackupDrill
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$evidencePath = Join-Path $projectPath 'artifacts/p1'
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null
$startedAt = [DateTimeOffset]::UtcNow
$checks = [ordered]@{}

Push-Location (Join-Path $projectPath 'backend')
try {
    & mvn -q test
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
    $checks.backendTests = 'PASS'
    & mvn -q checkstyle:check
    if ($LASTEXITCODE -ne 0) { throw 'Backend checkstyle failed.' }
    $checks.backendCheckstyle = 'PASS'
} finally {
    Pop-Location
}

if ($IncludeFrontend) {
    Push-Location (Join-Path $projectPath 'frontend')
    try {
        & npm run check
        if ($LASTEXITCODE -ne 0) { throw 'Frontend type check failed.' }
        & npm test -- --run
        if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        $checks.frontend = 'PASS'
    } finally {
        Pop-Location
    }
}

if ($IncludeInfrastructure) {
    & (Join-Path $PSScriptRoot 'Invoke-CommercialDatabaseAcceptance.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Commercial database acceptance failed.' }
    $checks.infrastructure = 'PASS'
}

if ($IncludeBackupDrill) {
    & (Join-Path $PSScriptRoot 'Invoke-BackupRestoreDrill.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Backup restore drill failed.' }
    $checks.backupRestore = 'PASS'
}

$result = [ordered]@{
    gate = 'P1_COMMERCIAL_PILOT'
    status = 'PASS'
    startedAt = $startedAt.ToString('o')
    finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    checks = $checks
}
$result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (
    Join-Path $evidencePath 'commercial-gate.json')
$result | ConvertTo-Json -Depth 5
