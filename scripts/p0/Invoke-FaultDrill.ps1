[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)]
    [ValidateSet('mysql', 'clickhouse', 'redis', 'worker')]
    [string]$Target,
    [Parameter(Mandatory)]
    [string]$ComposeFile,
    [string]$HealthUrl = 'http://127.0.0.1:8080/api/system/status',
    [int]$RecoveryTimeoutSeconds = 120,
    [string]$EvidenceDirectory = 'artifacts/p0/fault-drills'
)

$ErrorActionPreference = 'Stop'
$composePath = (Resolve-Path -LiteralPath $ComposeFile).Path
$evidencePath = [System.IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null
$started = Get-Date
$service = if ($Target -eq 'worker') { 'ai-worker' } else { $Target }

if (-not $PSCmdlet.ShouldProcess($service, '停止并重新启动服务以执行 P0 故障恢复演练')) {
    return
}

docker compose -f $composePath stop $service
if ($LASTEXITCODE -ne 0) { throw "停止 $service 失败" }
$failureObservedAt = Get-Date
docker compose -f $composePath up -d $service
if ($LASTEXITCODE -ne 0) { throw "启动 $service 失败" }

$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
$recoveredAt = $null
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            $recoveredAt = Get-Date
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $recoveredAt) { throw "$service 未在 $RecoveryTimeoutSeconds 秒内恢复" }

$evidence = [ordered]@{
    target = $Target
    composeFile = $composePath
    startedAt = $started.ToString('o')
    failureObservedAt = $failureObservedAt.ToString('o')
    recoveredAt = $recoveredAt.ToString('o')
    recoverySeconds = [math]::Round(($recoveredAt - $failureObservedAt).TotalSeconds, 2)
    healthUrl = $HealthUrl
    result = 'PASS'
}
$file = Join-Path $evidencePath ("{0}-{1}.json" -f $Target, (Get-Date -Format 'yyyyMMdd-HHmmss'))
$evidence | ConvertTo-Json | Set-Content -LiteralPath $file -Encoding utf8
Write-Output $file

