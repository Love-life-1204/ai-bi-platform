[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SshHost,
    [string]$SshUser = 'root',
    [string]$KnownHostsFile = '',
    [string]$IdentityFile = ''
)

$ErrorActionPreference = 'Stop'
$infrastructureDirectory = Split-Path -Parent $PSScriptRoot
$repositoryDirectory = Split-Path -Parent $infrastructureDirectory
$backendDirectory = Join-Path $repositoryDirectory 'backend'
$environmentFile = Join-Path $backendDirectory '.env.rabbitmq.local'
$trustStore = Join-Path $infrastructureDirectory 'docker/rabbitmq/client/ca.p12'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw "RabbitMQ client environment file is missing: $environmentFile"
}
if (-not (Test-Path -LiteralPath $trustStore)) {
    throw "RabbitMQ trust store is missing: $trustStore"
}
foreach ($line in Get-Content -LiteralPath $environmentFile) {
    if ($line -and -not $line.StartsWith('#')) {
        $pair = $line.Split('=', 2)
        if ($pair.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
        }
    }
}
$env:RABBITMQ_LIVE_TEST = 'true'
$env:RABBITMQ_HOST = 'localhost'
$env:RABBITMQ_PORT = '5671'
$env:RABBITMQ_TRUST_STORE = $trustStore

$sshArguments = @(
    '-N', '-T',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'ServerAliveInterval=15',
    '-o', 'ServerAliveCountMax=4',
    '-o', 'StrictHostKeyChecking=yes'
)
if ($KnownHostsFile) {
    $sshArguments += @('-o', "UserKnownHostsFile=$KnownHostsFile")
}
if ($IdentityFile) {
    $sshArguments += @('-i', $IdentityFile)
}
$sshArguments += @('-L', '5671:127.0.0.1:5671', "$SshUser@$SshHost")

$tunnel = $null
$tunnelError = Join-Path ([IO.Path]::GetTempPath()) "aibi-rabbit-tunnel-$PID.err"
try {
    $tunnel = Start-Process -FilePath 'ssh.exe' -ArgumentList $sshArguments `
        -WindowStyle Hidden -RedirectStandardError $tunnelError -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Seconds 1
        $tunnel.Refresh()
        if ($tunnel.HasExited) {
            break
        }
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $pending = $client.BeginConnect('127.0.0.1', 5671, $null, $null)
            if ($pending.AsyncWaitHandle.WaitOne(500)) {
                $client.EndConnect($pending)
                $ready = $true
                $client.Dispose()
                break
            }
            $client.Dispose()
        } catch {
            # Retry until the bounded readiness deadline.
        }
    }
    if (-not $ready) {
        $detail = if (Test-Path -LiteralPath $tunnelError) {
            (Get-Content -LiteralPath $tunnelError -Tail 20) -join [Environment]::NewLine
        } else {
            'No SSH diagnostic output was captured.'
        }
        throw "RabbitMQ SSH tunnel did not become ready within 20 seconds.`n$detail"
    }
    Push-Location $backendDirectory
    try {
        & mvn '-Dmaven.repo.local=.m2' '-Dtest=RabbitMqLiveAcceptanceTest' test
        if ($LASTEXITCODE -ne 0) {
            throw 'RabbitMQ live acceptance tests failed.'
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($tunnel -and -not $tunnel.HasExited) {
        Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $tunnelError) {
        Remove-Item -LiteralPath $tunnelError -Force -ErrorAction SilentlyContinue
    }
}
