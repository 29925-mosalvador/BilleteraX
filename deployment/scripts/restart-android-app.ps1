param(
    [string]$AndroidSdk = (Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk'),
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$adbTool = Join-Path $AndroidSdk 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adbTool)) { throw "No se encontro ADB en $adbTool" }

$devices = @(& $adbTool devices)
if ($LASTEXITCODE -ne 0) { throw 'No fue posible consultar ADB' }
$online = @($devices | Where-Object { $_ -match '^\S+\s+device\s*$' } | ForEach-Object { ($_ -split '\s+')[0] })
if (-not $Serial) {
    if ($online.Count -ne 1) { throw 'Inicia un emulador y especifica -Serial si hay mas de un dispositivo conectado' }
    $Serial = $online[0]
}
if ($Serial -notin $online) { throw "El dispositivo $Serial no esta conectado y autorizado" }

$package = 'ec.edu.espe.billeterax'
$installed = & $adbTool -s $Serial shell pm path $package
if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) {
    throw 'Instala BilleteraX desde Android Studio con Run antes de usar este comando'
}
# Force-stop affects only BilleteraX and preserves Room, configuration and other apps.
& $adbTool -s $Serial shell am force-stop $package
if ($LASTEXITCODE -ne 0) { throw 'Android no pudo detener BilleteraX; usa Cold Boot en Device Manager' }
& $adbTool -s $Serial shell am start -n "$package/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Android no pudo iniciar la actividad; revisa Logcat' }
Write-Output 'Solicitud de inicio enviada. Verifica la pantalla del emulador o continua con Run en Android Studio.'
