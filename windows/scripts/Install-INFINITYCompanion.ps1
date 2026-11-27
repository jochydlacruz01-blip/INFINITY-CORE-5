#requires -Version 7.4
#requires -RunAsAdministrator
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PublishDirectory,
    [Parameter(Mandatory)][string]$ServerCertificatePfx,
    [Parameter(Mandatory)][string]$BindAddress,
    [Parameter(Mandatory)][guid]$DeviceId,
    [ValidateRange(1024, 65535)][int]$Port = 7443,
    [string]$CertificateHostName,
    [string]$RemoteAddress = "LocalSubnet"
)

$ErrorActionPreference = "Stop"
$serviceName = "INFINITY Windows Companion"
$firewallRule = "INFINITY Windows Companion HTTPS"
$installRoot = Join-Path $env:ProgramFiles "INFINITY\Windows Companion"
$dataRoot = Join-Path $env:ProgramData "INFINITY\WindowsCompanion"
$exeName = "Infinity.WindowsCompanion.exe"

function Invoke-Sc([string[]]$Arguments) {
    & sc.exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "sc.exe failed ($LASTEXITCODE): $($Arguments -join ' ')" }
}

$address = [System.Net.IPAddress]::Parse($BindAddress)
$octets = $address.GetAddressBytes()
$privateAddress = [System.Net.IPAddress]::IsLoopback($address)
if ($address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
    $privateAddress = $privateAddress -or $octets[0] -eq 10 -or
        ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
        ($octets[0] -eq 192 -and $octets[1] -eq 168) -or
        ($octets[0] -eq 100 -and $octets[1] -ge 64 -and $octets[1] -le 127)
} elseif ($address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetworkV6) {
    $privateAddress = $privateAddress -or (($octets[0] -band 0xFE) -eq 0xFC)
}
if (-not $privateAddress) { throw "BindAddress must be loopback, RFC1918, CGNAT or IPv6 ULA. Public and link-local addresses are rejected." }
if ($DeviceId -eq [guid]::Empty) { throw "DeviceId must be a non-empty GUID." }
if (-not [System.Net.IPAddress]::IsLoopback($address) -and
    -not (Get-NetIPAddress -IPAddress $BindAddress -ErrorAction SilentlyContinue)) {
    throw "BindAddress is not assigned to a local network interface."
}
if ([string]::IsNullOrWhiteSpace($CertificateHostName)) { $CertificateHostName = $BindAddress }

$publish = (Resolve-Path -LiteralPath $PublishDirectory).Path
$sourceExe = Join-Path $publish $exeName
if (-not (Test-Path -LiteralPath $sourceExe -PathType Leaf)) { throw "Published executable not found: $sourceExe" }
$pfxSource = (Resolve-Path -LiteralPath $ServerCertificatePfx).Path
if (-not (Test-Path -LiteralPath $pfxSource -PathType Leaf)) { throw "PFX file not found." }

New-Item -ItemType Directory -Force -Path $installRoot, $dataRoot | Out-Null
& icacls.exe $dataRoot /inheritance:r /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not secure the Companion data directory ACL." }

Copy-Item -Path (Join-Path $publish "*") -Destination $installRoot -Recurse -Force
$pfxTarget = Join-Path $dataRoot "server.pfx"
Copy-Item -LiteralPath $pfxSource -Destination $pfxTarget -Force

# The password is encrypted with machine-scope DPAPI; plaintext is never written to the registry or disk.
$protectedAssembly = Join-Path $publish "System.Security.Cryptography.ProtectedData.dll"
if (Test-Path -LiteralPath $protectedAssembly) { Add-Type -Path $protectedAssembly }
$securePassword = Read-Host "PFX password (stored only as machine-DPAPI ciphertext)" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$passwordBytes = $null
$protectedBytes = $null
$plainPassword = $null
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $passwordBytes = [Text.UTF8Encoding]::new($false, $true).GetBytes($plainPassword)
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        $pfxTarget,
        $plainPassword,
        [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    try {
        $ekuRaw = $certificate.Extensions | Where-Object { $_.Oid.Value -eq "2.5.29.37" } | Select-Object -First 1
        $sanRaw = $certificate.Extensions | Where-Object { $_.Oid.Value -eq "2.5.29.17" } | Select-Object -First 1
        if ($null -eq $ekuRaw -or $null -eq $sanRaw) {
            throw "PFX certificate must contain serverAuth EKU and subjectAltName."
        }
        $eku = [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new($ekuRaw, $ekuRaw.Critical)
        $serverEku = @($eku.EnhancedKeyUsages | Where-Object { $_.Value -eq "1.3.6.1.5.5.7.3.1" }).Count -gt 0
        $keyUsageRaw = $certificate.Extensions | Where-Object { $_.Oid.Value -eq "2.5.29.15" } | Select-Object -First 1
        $allowsDigitalSignature = $true
        if ($null -ne $keyUsageRaw) {
            $keyUsage = [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new($keyUsageRaw, $keyUsageRaw.Critical)
            $allowsDigitalSignature = $keyUsage.KeyUsages.HasFlag([System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature)
        }
        $san = [System.Security.Cryptography.X509Certificates.X509SubjectAlternativeNameExtension]::new($sanRaw, $sanRaw.Critical)
        $expectedIp = $null
        $hostMatches = $false
        if ([System.Net.IPAddress]::TryParse($CertificateHostName, [ref]$expectedIp)) {
            $hostMatches = @($san.EnumerateIPAddresses() | ForEach-Object { $_.ToString() }) -contains $expectedIp.ToString()
        } else {
            $hostMatches = @($san.EnumerateDnsNames()) -contains $CertificateHostName
        }
        $now = [DateTime]::UtcNow
        if (-not $certificate.HasPrivateKey -or
            $now -lt $certificate.NotBefore.ToUniversalTime() -or
            $now -gt $certificate.NotAfter.ToUniversalTime() -or
            -not $serverEku -or -not $allowsDigitalSignature -or -not $hostMatches) {
            throw "PFX must contain a current TLS server certificate/private key with serverAuth EKU and SAN matching CertificateHostName."
        }
        Write-Host "TLS server certificate SHA-256: $($certificate.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256))"
    } finally {
        $certificate.Dispose()
    }
    $protectedBytes = [System.Security.Cryptography.ProtectedData]::Protect(
        $passwordBytes, $null, [System.Security.Cryptography.DataProtectionScope]::LocalMachine)
    $secretPath = Join-Path $dataRoot "server-password.dpapi"
    [IO.File]::WriteAllBytes($secretPath, $protectedBytes)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    if ($passwordBytes) { [System.Security.Cryptography.CryptographicOperations]::ZeroMemory($passwordBytes) }
    if ($protectedBytes) { [System.Security.Cryptography.CryptographicOperations]::ZeroMemory($protectedBytes) }
    $plainPassword = $null
    $securePassword.Dispose()
}

[Environment]::SetEnvironmentVariable("INFINITY_BIND_ADDRESS", $BindAddress, "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_PORT", $Port.ToString(), "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_DEVICE_ID", $DeviceId.ToString("D"), "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_SERVER_CERTIFICATE_PFX", $pfxTarget, "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_SERVER_CERTIFICATE_PASSWORD_FILE", $secretPath, "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_SERVER_CERTIFICATE_PASSWORD", $null, "Machine")
# Explicitly preserve the fail-closed feature policy on every installation/upgrade.
[Environment]::SetEnvironmentVariable("INFINITY_ENABLE_REMOTE_DISPLAY", "false", "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_ENABLE_REMOTE_INPUT", "false", "Machine")
[Environment]::SetEnvironmentVariable("INFINITY_ENABLE_FILE_TRANSFER", "false", "Machine")

$exe = Join-Path $installRoot $exeName
$existing = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($null -eq $existing) {
    Invoke-Sc @("create", $serviceName, "binPath=", "`"$exe`"", "start=", "delayed-auto", "obj=", "LocalSystem")
} else {
    if ($existing.Status -ne "Stopped") {
        Stop-Service -Name $serviceName -Force
        $existing.WaitForStatus("Stopped", [TimeSpan]::FromSeconds(30))
    }
    Invoke-Sc @("config", $serviceName, "binPath=", "`"$exe`"", "start=", "delayed-auto", "obj=", "LocalSystem")
}
Invoke-Sc @("description", $serviceName, "Secure INFINITY Companion endpoint; TLS 1.3 mutual authentication required.")
Invoke-Sc @("failure", $serviceName, "reset=", "86400", "actions=", "restart/5000/restart/15000/restart/60000")
Invoke-Sc @("failureflag", $serviceName, "1")

Remove-NetFirewallRule -DisplayName $firewallRule -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName $firewallRule -Direction Inbound -Action Allow `
    -Program $exe -Protocol TCP -LocalAddress $BindAddress -LocalPort $Port `
    -RemoteAddress $RemoteAddress -Profile Private | Out-Null

Invoke-Sc @("start", $serviceName)
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 1
    $service = Get-Service -Name $serviceName
    if ($service.Status -eq "Running") { break }
} while ((Get-Date) -lt $deadline)
if ($service.Status -ne "Running") {
    throw "Service did not reach Running state. Inspect Event Viewer > Windows Logs > Application and the INFINITY audit directory."
}

Write-Host "INFINITY Windows Companion installed and running." -ForegroundColor Green
Write-Host "Endpoint: https://$($address.ToString()):$Port (mTLS required)"
Write-Host "Certificate host: $CertificateHostName"
Write-Host "Firewall remote scope: $RemoteAddress"
Write-Host "SCM recovery: restart after 5s, 15s, then 60s."
Write-Host "Service identity: LocalSystem. Keep the host patched and ProgramData ACL restricted."
