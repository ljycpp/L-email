param(
    [switch]$SkipNpmInstall,
    [switch]$SkipDbInit,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "start-l-email.ps1"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Starter script not found: $scriptPath"
}

& $scriptPath
