param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$AndroidUserHome = "$env:USERPROFILE\.android"
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) { throw 'Se requiere JDK 21' }
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
# Share Android Studio's debug signing key so updates preserve installed app data.
$env:ANDROID_USER_HOME = $AndroidUserHome
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
$env:ANDROID_HOME = $AndroidSdk
& (Join-Path $projectRoot 'backend/gradlew.bat') -p (Join-Path $projectRoot 'backend') test bootJar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o pruebas backend' }
& (Join-Path $projectRoot 'android/gradlew.bat') -p (Join-Path $projectRoot 'android') assembleDebug assembleDebugAndroidTest testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o pruebas Android' }
Write-Output 'JAR: backend/build/libs/billeterax.jar'
Write-Output 'APK: android/app/build/outputs/apk/debug/app-debug.apk'
