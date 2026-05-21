$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "mail-system-backend"

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\anaconda\envs\re2nfa-java\Library" }
$mavenCmd = Join-Path $javaHome "bin\mvn.cmd"
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

Write-Host "==> Compiling backend..." -ForegroundColor Cyan
& $mavenCmd -gs maven-settings.xml -q -DskipTests compile

if ($LASTEXITCODE -ne 0) {
    throw "Backend compile failed. Please check the Maven output above."
}

Write-Host "==> Building backend classpath..." -ForegroundColor Cyan
& $mavenCmd -gs maven-settings.xml -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"

if ($LASTEXITCODE -ne 0) {
    throw "Backend classpath build failed. Please check the Maven output above."
}

$classpath = "target\classes;" + (Get-Content "target\classpath.txt" -Raw -Encoding UTF8)

Write-Host "==> Starting backend service at http://localhost:8080" -ForegroundColor Green
& $javaExe -cp $classpath com.practice.mailsystem.MailSystemApplication
