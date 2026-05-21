$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot "vue-mail-front"

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

Set-Location $frontendDir

Write-Host "==> Starting frontend dev server at http://localhost:8081" -ForegroundColor Green
npm run dev
