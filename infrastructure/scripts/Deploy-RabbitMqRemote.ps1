[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SshHost,
    [string]$SshUser = 'root',
    [string]$RemoteDirectory = '/opt/ai-bi/rabbitmq',
    [string]$KnownHostsFile = '',
    [string]$IdentityFile = ''
)

$ErrorActionPreference = 'Stop'
$infrastructureDirectory = Split-Path -Parent $PSScriptRoot
$dockerDirectory = Join-Path $infrastructureDirectory 'docker'
$rabbitDirectory = Join-Path $dockerDirectory 'rabbitmq'
$composeFile = Join-Path $dockerDirectory 'rabbitmq.compose.yaml'
$configurationFile = Join-Path $rabbitDirectory 'rabbitmq.conf'
$environmentFile = Join-Path $rabbitDirectory '.env'
$bootstrapFile = Join-Path $PSScriptRoot 'deploy_rabbitmq_remote.sh'
$clientDirectory = Join-Path $rabbitDirectory 'client'
$trustStore = Join-Path $clientDirectory 'ca.p12'

foreach ($file in @($composeFile, $configurationFile, $environmentFile, $bootstrapFile)) {
    if (-not (Test-Path -LiteralPath $file)) {
        throw "Required deployment file is missing: $file"
    }
}

$sshOptions = @('-o', 'StrictHostKeyChecking=yes')
if ($KnownHostsFile) {
    $sshOptions += @('-o', "UserKnownHostsFile=$KnownHostsFile")
}
if ($IdentityFile) {
    $sshOptions += @('-i', $IdentityFile)
}
$target = "$SshUser@$SshHost"

& ssh.exe @sshOptions $target "install -d -m 700 '$RemoteDirectory/rabbitmq/tls'"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to prepare the remote RabbitMQ directory.'
}

& scp.exe @sshOptions $composeFile "${target}:$RemoteDirectory/compose.yaml"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to upload the RabbitMQ Compose file.'
}
& scp.exe @sshOptions $configurationFile `
    "${target}:$RemoteDirectory/rabbitmq/rabbitmq.conf"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to upload the RabbitMQ configuration.'
}
& scp.exe @sshOptions $environmentFile "${target}:$RemoteDirectory/.env"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to upload the RabbitMQ environment file.'
}
& scp.exe @sshOptions $bootstrapFile "${target}:$RemoteDirectory/deploy-rabbitmq.sh"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to upload the RabbitMQ bootstrap script.'
}

& ssh.exe @sshOptions $target `
    "chmod 600 '$RemoteDirectory/.env'; chmod 700 '$RemoteDirectory/deploy-rabbitmq.sh'; bash '$RemoteDirectory/deploy-rabbitmq.sh'"
if ($LASTEXITCODE -ne 0) {
    throw 'Remote RabbitMQ bootstrap failed.'
}

New-Item -ItemType Directory -Force -Path $clientDirectory | Out-Null
& scp.exe @sshOptions "${target}:$RemoteDirectory/rabbitmq/tls/ca.pem" $clientDirectory
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to download the RabbitMQ CA certificate.'
}

foreach ($line in Get-Content -LiteralPath $environmentFile) {
    if ($line -and -not $line.StartsWith('#')) {
        $pair = $line.Split('=', 2)
        if ($pair.Count -eq 2 -and $pair[0] -eq 'RABBITMQ_TRUST_STORE_PASSWORD') {
            $env:RABBITMQ_TRUST_STORE_PASSWORD = $pair[1]
        }
    }
}
if (-not $env:RABBITMQ_TRUST_STORE_PASSWORD) {
    throw 'RABBITMQ_TRUST_STORE_PASSWORD is missing from the local RabbitMQ .env file.'
}
$keytool = Get-Command keytool -ErrorAction Stop
if (Test-Path -LiteralPath $trustStore) {
    Remove-Item -LiteralPath $trustStore -Force
}
& $keytool.Source -importcert -noprompt -alias rabbitmq-local-ca `
    -file (Join-Path $clientDirectory 'ca.pem') -keystore $trustStore `
    -storetype PKCS12 -storepass:env RABBITMQ_TRUST_STORE_PASSWORD
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to build the local Java PKCS12 trust store.'
}

Write-Host "RabbitMQ deployed. Local trust store: $trustStore"

