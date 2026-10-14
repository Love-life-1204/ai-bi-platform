[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SshHost,
    [string]$SshUser = 'root',
    [int]$LocalAmqpPort = 5671,
    [int]$LocalManagementPort = 15672,
    [string]$KnownHostsFile = '',
    [string]$IdentityFile = ''
)

$ErrorActionPreference = 'Stop'
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
$sshArguments += @(
    '-L', "${LocalAmqpPort}:127.0.0.1:5671",
    '-L', "${LocalManagementPort}:127.0.0.1:15672",
    "$SshUser@$SshHost"
)

Write-Host "RabbitMQ TLS: localhost:$LocalAmqpPort"
Write-Host "RabbitMQ management: http://localhost:$LocalManagementPort"
Write-Host 'Keep this terminal open; press Ctrl+C to stop the tunnel.'
& ssh.exe @sshArguments
exit $LASTEXITCODE

