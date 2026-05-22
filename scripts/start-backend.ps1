$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "mail-system-backend"

$javaHome = "C:\Program Files\Java\jdk-21.0.11"
if (-not (Test-Path $javaHome)) {
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        $javaHome = $env:JAVA_HOME
    } else {
        $javaHome = "D:\anaconda\envs\re2nfa-java\Library"
    }
}
$mavenCmd = try { (Get-Command mvn -ErrorAction SilentlyContinue).Path } catch { $null }
if (-not $mavenCmd) {
    $mavenCmd = Join-Path $javaHome "bin\mvn.cmd"
}
$javaExe = Join-Path $javaHome "bin\java.exe"

if (-not (Test-Path $backendDir)) {
    throw "Backend directory not found: $backendDir"
}

if (-not (Test-Path $mavenCmd)) {
    throw "Maven command not found: $mavenCmd"
}

if (-not (Test-Path $javaExe)) {
    throw "Java executable not found: $javaExe"
}

Set-Location $backendDir

$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path

if (-not $env:MAIL_DB_PASSWORD) {
    $env:MAIL_DB_PASSWORD = "123456"
}

if (-not $env:APP_JWT_SECRET) {
    $env:APP_JWT_SECRET = "l-email-dev-jwt-secret-2026-safe-minimum-32"
}

if (-not $env:APP_AI_SECRET_KEY) {
    $env:APP_AI_SECRET_KEY = "l-email-ai-secret-key-2026-32!!!"
}

Write-Host "==> Stopping previous backend process if it exists..." -ForegroundColor Cyan
Get-Process java, javaw -ErrorAction SilentlyContinue |
    Stop-Process -Force -ErrorAction SilentlyContinue

Start-Sleep -Milliseconds 800

Write-Host "==> Starting backend service at http://localhost:8080" -ForegroundColor Green
& $mavenCmd -gs maven-settings.xml clean spring-boot:run
