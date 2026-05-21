$ErrorActionPreference = "Stop"

$scriptDir = $PSScriptRoot
$projectRoot = Split-Path -Parent $scriptDir

function Test-PortOpen {
    param(
        [string]$HostName,
        [int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        $wait = $async.AsyncWaitHandle.WaitOne(800)
        if (-not $wait) {
            $client.Close()
            return $false
        }
        $client.EndConnect($async) | Out-Null
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Test-LocalPortOpen {
    param(
        [int]$Port
    )

    $netstatHit = netstat -ano | Select-String ":$Port\s+.*LISTENING"
    if ($netstatHit) {
        return $true
    }

    return (Test-PortOpen -HostName "127.0.0.1" -Port $Port) -or (Test-PortOpen -HostName "localhost" -Port $Port)
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   L-email One-Click Starter" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-LocalPortOpen -Port 3306)) {
    Write-Warning "MySQL port 3306 is not responding. Please make sure MySQL is running first."
}

$windowsPowerShell = "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"

if (Test-LocalPortOpen -Port 8080) {
    Write-Host "==> Backend is already running at http://localhost:8080, skipping launch." -ForegroundColor Green
} else {
    Write-Host "==> Launching backend service..." -ForegroundColor Yellow
    Start-Process -FilePath $windowsPowerShell -WorkingDirectory $projectRoot -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $scriptDir "start-backend.ps1")
    )
}

Start-Sleep -Seconds 3

if (Test-LocalPortOpen -Port 8081) {
    Write-Host "==> Frontend is already running at http://localhost:8081, skipping launch." -ForegroundColor Green
} else {
    Write-Host "==> Launching frontend dev server..." -ForegroundColor Yellow
    Start-Process -FilePath $windowsPowerShell -WorkingDirectory $projectRoot -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $scriptDir "start-frontend.ps1")
    )
}

Write-Host ""
Write-Host "Two windows were started:" -ForegroundColor Green
Write-Host "1. Backend service        http://localhost:8080"
Write-Host "2. Frontend dev server    http://localhost:8081"
Write-Host ""
Write-Host "Demo account: admin@lmailbox.com / 123456" -ForegroundColor Green
Write-Host "Spam detection now runs inside the backend using the built-in model plugin." -ForegroundColor DarkYellow
