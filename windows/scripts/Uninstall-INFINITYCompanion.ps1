#requires -Version 7.2
#requires -RunAsAdministrator
[CmdletBinding()]
param([switch]$RemoveProtectedPassword)

$ErrorActionPreference = "Stop"
$serviceName = "INFINITY Windows Companion"
$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($null -ne $service) {
    if ($service.Status -ne "Stopped") {
        Stop-Service -Name $serviceName -Force
        $service.WaitForStatus("Stopped", [TimeSpan]::FromSeconds(30))
    }
    & sc.exe delete $serviceName
    if ($LASTEXITCODE -ne 0) { throw "Could not remove the Windows service." }
}
Remove-NetFirewallRule -DisplayName "INFINITY Windows Companion HTTPS" -ErrorAction SilentlyContinue

foreach ($name in @(
    "INFINITY_BIND_ADDRESS",
    "INFINITY_PORT",
    "INFINITY_DEVICE_ID",
    "INFINITY_SERVER_CERTIFICATE_PFX",
    "INFINITY_SERVER_CERTIFICATE_PASSWORD_FILE",
    "INFINITY_SERVER_CERTIFICATE_PASSWORD",
    "INFINITY_ENABLE_REMOTE_DISPLAY",
    "INFINITY_ENABLE_REMOTE_INPUT",
    "INFINITY_ENABLE_FILE_TRANSFER"
)) {
    [Environment]::SetEnvironmentVariable($name, $null, "Machine")
}

$installRoot = Join-Path $env:ProgramFiles "INFINITY\Windows Companion"
$dataRoot = Join-Path $env:ProgramData "INFINITY\WindowsCompanion"
if (Test-Path -LiteralPath $installRoot) { Remove-Item -LiteralPath $installRoot -Recurse -Force }
if ($RemoveProtectedPassword -and (Test-Path -LiteralPath $dataRoot)) {
    Remove-Item -LiteralPath (Join-Path $dataRoot "server-password.dpapi") -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $dataRoot "server.pfx") -Force -ErrorAction SilentlyContinue
}
Write-Host "Companion service and firewall rule removed. CA, authorization registry and audit history were preserved." -ForegroundColor Yellow
