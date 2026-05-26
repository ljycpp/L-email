param(
    [switch]$SkipNpmInstall,
    [switch]$SkipDbInit,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$SpamDir = Join-Path $Root "email-spam"
$BackendDir = Join-Path $Root "mail-system-backend"
$FrontendDir = Join-Path $Root "vue-mail-front"
$SchemaPath = Join-Path $BackendDir "src\main\resources\db\schema.sql"
$JavaHome = "E:\JAVA\IDEA\IntelliJ IDEA 2025.1.1.1\jbr"
$MavenCmd = "E:\JAVA\IDEA\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
$DbName = "mail_system"
$DbUser = "root"
$DbPassword = "jy@051116"
$JwtSecret = "l-email-development-jwt-secret-key-2026-at-least-32-bytes"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Warn {
    param([string]$Message)
    Write-Host "WARN: $Message" -ForegroundColor Yellow
}

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Start-ServiceWindow {
    param(
        [string]$Title,
        [string]$WorkingDirectory,
        [string]$Command
    )

    $windowCommand = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
Set-Location -LiteralPath '$WorkingDirectory'
$Command
"@

    $runnerDir = Join-Path $env:TEMP "l-email-dev-runners"
    New-Item -ItemType Directory -Path $runnerDir -Force | Out-Null
    $runnerName = ($Title -replace "[^\p{L}\p{Nd}\-_\.]", "_") + ".ps1"
    $runnerPath = Join-Path $runnerDir $runnerName
    Set-Content -LiteralPath $runnerPath -Value $windowCommand -Encoding UTF8

    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-File", $runnerPath
    )
}

function Try-StartMySqlService {
    Write-Step "Checking MySQL service"
    $service = Get-Service |
        Where-Object { $_.Name -match "mysql" -or $_.DisplayName -match "mysql" } |
        Select-Object -First 1

    if ($null -eq $service) {
        Write-Warn "No MySQL Windows service was found. Please start MySQL manually in Workbench or Services."
        return
    }

    if ($service.Status -eq "Running") {
        Write-Host "MySQL service is already running: $($service.Name)"
        return
    }

    try {
        Start-Service -Name $service.Name
        Write-Host "Started MySQL service: $($service.Name)"
    } catch {
        Write-Warn "Failed to start MySQL service automatically. Run this script as administrator or start MySQL manually."
    }
}

function Initialize-Database {
    if ($SkipDbInit) {
        Write-Step "Skipping database initialization"
        return
    }

    Write-Step "Checking database and schema"
    if (-not (Test-Command "mysql.exe")) {
        Write-Warn "mysql.exe was not found in PATH. Please confirm database '$DbName' and run schema.sql manually if needed:"
        Write-Host "      $SchemaPath"
        return
    }

    $tableCheckSql = "CREATE DATABASE IF NOT EXISTS $DbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName' AND table_name IN ('sys_user','mail_message','mail_user_box','mail_recipient','mail_attachment','mail_label','mail_contact','user_ai_config','mail_ai_record');"
    $checkOutput = & mysql.exe -h 127.0.0.1 -P 3306 -u $DbUser "-p$DbPassword" -N -e $tableCheckSql 2>$null

    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Could not connect to MySQL with user '$DbUser'. Please verify MySQL is running and the password is correct."
        return
    }

    $tableCount = 0
    if ($checkOutput) {
        $lastLine = @($checkOutput)[-1]
        [int]::TryParse($lastLine, [ref]$tableCount) | Out-Null
    }

    if ($tableCount -ge 9) {
        Write-Host "Database '$DbName' and required tables already exist."
        return
    }

    Write-Host "Required tables are missing. Running schema.sql..."
    $schemaForMysql = $SchemaPath.Replace("\", "/")
    & mysql.exe -h 127.0.0.1 -P 3306 -u $DbUser "-p$DbPassword" $DbName "--default-character-set=utf8mb4" -e "source $schemaForMysql"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "schema.sql executed successfully."
    } else {
        Write-Warn "schema.sql execution failed. Please run it manually in MySQL Workbench."
    }
}

function Assert-File {
    param([string]$Path, [string]$Name)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Name not found: $Path"
    }
}

Write-Step "Validating required paths"
Assert-File (Join-Path $SpamDir "web_app.py") "Spam service entry"
Assert-File (Join-Path $BackendDir "pom.xml") "Backend pom.xml"
Assert-File (Join-Path $FrontendDir "package.json") "Frontend package.json"
Assert-File $SchemaPath "Database schema"
Assert-File (Join-Path $JavaHome "bin\java.exe") "Java 21 java.exe"
Assert-File $MavenCmd "IntelliJ bundled Maven"

Try-StartMySqlService
Initialize-Database

Write-Step "Opening spam detection service window"
Start-ServiceWindow -Title "L-email spam service :8000" -WorkingDirectory $SpamDir -Command @"
python .\web_app.py
"@

Write-Step "Opening backend service window"
Start-ServiceWindow -Title "L-email backend :8080" -WorkingDirectory $BackendDir -Command @"
`$env:JAVA_HOME = '$JavaHome'
`$env:Path = "`$env:JAVA_HOME\bin;`$env:Path"
`$env:MAIL_DB_PASSWORD = '$DbPassword'
`$env:APP_JWT_SECRET = '$JwtSecret'
& '$MavenCmd' dependency:build-classpath "-Dmdep.outputFile=cp.txt"
if (`$LASTEXITCODE -ne 0) { throw 'Failed to build Maven classpath.' }
`$cp = Get-Content .\cp.txt -Raw -Encoding UTF8
& "`$env:JAVA_HOME\bin\java.exe" -cp "target\classes;`$cp" com.practice.mailsystem.MailSystemApplication
"@

Write-Step "Opening frontend service window"
$npmInstallCommand = if ($SkipNpmInstall) {
    "Write-Host 'Skipping npm install.'"
} else {
    @"
npm.cmd install
if (`$LASTEXITCODE -ne 0) { throw 'npm install failed.' }
"@
}
Start-ServiceWindow -Title "L-email frontend :8081" -WorkingDirectory $FrontendDir -Command @"
$npmInstallCommand
npm.cmd run dev
"@

if (-not $NoBrowser) {
    Write-Step "Browser will open after a short delay"
    Start-Job -ScriptBlock {
        Start-Sleep -Seconds 12
        Start-Process "http://localhost:8081"
    } | Out-Null
}

Write-Host ""
Write-Host "All service windows have been opened." -ForegroundColor Green
Write-Host "Check these URLs manually:"
Write-Host "  Spam service: http://127.0.0.1:8000"
Write-Host "  Frontend:     http://localhost:8081"
Write-Host ""
Write-Host "Useful options:"
Write-Host "  .\scripts\start-dev.ps1 -SkipNpmInstall"
Write-Host "  .\scripts\start-dev.ps1 -SkipDbInit"
Write-Host "  .\scripts\start-dev.ps1 -NoBrowser"
