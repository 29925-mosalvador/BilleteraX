param([Parameter(Mandatory=$true)][string]$JavaHome, [int]$Port = 8080, [switch]$FreshDemo)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jar = Join-Path $projectRoot 'backend/build/libs/billeterax.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Compilar primero el backend' }
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = "$Port"
# A reset selects a NEW database instead of deleting financial records.
$dataName = if ($FreshDemo) { 'demo-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-ffff') } else { 'billeterax' }
$dataPath = (Join-Path $projectRoot "backend/data/$dataName").Replace('\','/')
$env:DB_URL = "jdbc:h2:file:$dataPath;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000"
Write-Output "Base de laboratorio: $dataPath"
& (Join-Path $JavaHome 'bin/java.exe') -jar $jar
