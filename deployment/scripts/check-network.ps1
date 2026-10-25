param(
    [Parameter(Mandatory=$true)][string]$DebianIP,
    [ValidateRange(1,65535)][int]$SshPort = 22,
    [ValidateRange(1,65535)][int]$ApiPort = 8080
)
$ErrorActionPreference = 'Continue'
Write-Output 'Interfaces Windows'
Get-NetIPAddress -AddressFamily IPv4 | Select-Object InterfaceAlias,IPAddress,PrefixLength
Write-Output 'SSH'
Test-NetConnection -ComputerName $DebianIP -Port $SshPort
Write-Output 'API REST y WebSocket'
Test-NetConnection -ComputerName $DebianIP -Port $ApiPort
Invoke-RestMethod -Uri "http://${DebianIP}:${ApiPort}/actuator/health" -TimeoutSec 10
