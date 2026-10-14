[CmdletBinding()]
param(
    [switch]$IncludeInfrastructure,
    [switch]$IncludeLoad,
    [string]$ReportPath = ""
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $ReportPath) {
    $ReportPath = Join-Path $repoPath 'artifacts/p0/p0-gate-result.md'
}
$reportDirectory = Split-Path $ReportPath -Parent
New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
$temporaryPath = Join-Path $repoPath 'artifacts/p0/tmp'
New-Item -ItemType Directory -Force -Path $temporaryPath | Out-Null

$results = [System.Collections.Generic.List[object]]::new()

function Invoke-GateStep {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Executable,
        [string[]]$Arguments
    )
    $started = Get-Date
    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $results.Add([pscustomobject]@{
        Name = $Name
        Status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
        ExitCode = $exitCode
        Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
        Command = "$Executable $($Arguments -join ' ')"
    })
    if ($exitCode -ne 0) {
        throw "P0 门禁失败：$Name（退出码 $exitCode）"
    }
}

$previousTmp = $env:TMP
$previousTemp = $env:TEMP
try {
    Invoke-GateStep 'Backend' (Join-Path $repoPath 'backend') 'mvn' @('verify')
    Invoke-GateStep 'Frontend' (Join-Path $repoPath 'frontend') 'npm' @('run', 'ci')
    Invoke-GateStep 'Frontend build' (Join-Path $repoPath 'frontend') 'npm' @('run', 'build')
    $env:TMP = $temporaryPath
    $env:TEMP = $temporaryPath
    $python = Join-Path $repoPath 'ai-service/.venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $python)) {
        throw '缺少 ai-service/.venv；请先按 ai-service/README.md 创建测试环境。'
    }
    $pytestTemp = Join-Path $temporaryPath ("pytest-" + [guid]::NewGuid().ToString('N'))
    Invoke-GateStep 'AI service' (Join-Path $repoPath 'ai-service') $python `
        @('-m', 'pytest', '-p', 'no:cacheprovider', '--basetemp', $pytestTemp, 'tests')
    if ($IncludeInfrastructure) {
        Invoke-GateStep 'Infrastructure' (Join-Path $repoPath 'infrastructure') `
            (Join-Path $repoPath 'infrastructure/.venv/Scripts/python.exe') `
            @('-m', 'pytest', 'tests')
    } else {
        $results.Add([pscustomobject]@{
            Name = 'Infrastructure'
            Status = 'NOT RUN'
            ExitCode = '-'
            Seconds = '-'
            Command = 'Re-run with -IncludeInfrastructure'
        })
    }
    if ($IncludeLoad) {
        if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
            throw 'IncludeLoad 需要 k6，并需设置 BASE_URL、TENANTS_JSON 与各业务对象 ID。'
        }
        Invoke-GateStep 'Mixed load' $repoPath 'k6' @('run', 'tests/performance/mixed-workload.js')
    } else {
        $results.Add([pscustomobject]@{
            Name = 'Mixed load'
            Status = 'NOT RUN'
            ExitCode = '-'
            Seconds = '-'
            Command = 'Re-run with -IncludeLoad'
        })
    }
} finally {
    $env:TMP = $previousTmp
    $env:TEMP = $previousTemp
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
    $lines = @('# P0 gate result', '', "Generated: $timestamp", '',
        '| Step | Status | Exit code | Seconds | Command |',
        '|---|---:|---:|---:|---|')
    foreach ($result in $results) {
        $lines += "| $($result.Name) | $($result.Status) | $($result.ExitCode) | $($result.Seconds) | ``$($result.Command)`` |"
    }
    $lines += ''
    $lines += if ($results.Status -contains 'FAIL') { 'Decision: **NO-GO**' } else { 'Decision: **GO for covered gates**' }
    Set-Content -LiteralPath $ReportPath -Value $lines -Encoding utf8
}

