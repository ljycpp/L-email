# Copy SQL scripts to ASCII-only paths (MySQL SOURCE on Windows fails on Chinese paths).
# Run from repo root:  .\scripts\copy-schema-for-mysql.ps1

$ErrorActionPreference = 'Stop'
$destDir = 'C:\mail-system-db'
$repoRoot = Split-Path -Parent $PSScriptRoot
$dbDir = Join-Path $repoRoot 'mail-system-backend\src\main\resources\db'

$files = @(
    @{ Name = 'schema.sql'; Src = Join-Path $dbDir 'schema.sql' },
    @{ Name = 'migration-ai.sql'; Src = Join-Path $dbDir 'migration-ai.sql' }
)

New-Item -ItemType Directory -Force -Path $destDir | Out-Null

foreach ($f in $files) {
    if (-not (Test-Path $f.Src)) {
        throw "Missing: $($f.Src)"
    }
    $dest = Join-Path $destDir $f.Name
    Copy-Item -LiteralPath $f.Src -Destination $dest -Force
    Write-Host "Copied: $dest" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Full rebuild (empty DB):' -ForegroundColor Cyan
Write-Host '  DROP DATABASE IF EXISTS mail_system;'
Write-Host '  CREATE DATABASE mail_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
Write-Host '  USE mail_system;'
Write-Host '  SOURCE C:/mail-system-db/schema.sql;'
Write-Host ''
Write-Host 'Existing DB - add AI tables only:' -ForegroundColor Cyan
Write-Host '  USE mail_system;'
Write-Host '  SOURCE C:/mail-system-db/migration-ai.sql;'
