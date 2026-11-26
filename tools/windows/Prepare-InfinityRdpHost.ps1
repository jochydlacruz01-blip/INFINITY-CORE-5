[CmdletBinding(SupportsShouldProcess=$true)]
param()
$ErrorActionPreference = "Stop"
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw "Open Windows PowerShell as Administrator." }
$os = Get-CimInstance Win32_OperatingSystem
$product = [string]$os.Caption
if ($product -match "Home") { throw "Windows Home cannot host Microsoft Remote Desktop sessions. Use Pro, Enterprise, or Education." }
if ($os.ProductType -ne 1 -or $product -notmatch "Windows 10|Windows 11") { throw "Only Windows 10/11 client editions are supported." }
$privateProfiles = @(Get-NetConnectionProfile | Where-Object { $_.NetworkCategory -eq "Private" })
if ($privateProfiles.Count -eq 0) { throw "No Private network profile found. Set the trusted LAN to Private in Windows Settings; this script will not change it." }
$rdpPath = "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server"
$nlaPath = "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server\WinStations\RDP-Tcp"
if (-not (Test-Path $nlaPath)) { throw "RDP listener configuration was not found." }
if ($PSCmdlet.ShouldProcess($product, "Enable RDP with NLA; restrict firewall to Private/LocalSubnet")) {
    Set-ItemProperty -Path $rdpPath -Name fDenyTSConnections -Value 0 -Type DWord
    Set-ItemProperty -Path $nlaPath -Name UserAuthentication -Value 1 -Type DWord
    $rules = @(Get-NetFirewallRule -Name "RemoteDesktop-UserMode-In-TCP","RemoteDesktop-UserMode-In-UDP" -ErrorAction SilentlyContinue)
    if ($rules.Count -eq 0) { throw "Built-in Remote Desktop firewall rules not found; no broad firewall rule will be created." }
    foreach ($rule in $rules) { Set-NetFirewallRule -Name $rule.Name -Enabled True -Profile Private -RemoteAddress LocalSubnet }
    Set-Service -Name TermService -StartupType Manual
    Start-Service -Name TermService
    $tcp = Get-NetTCPConnection -State Listen -LocalPort 3389 -ErrorAction SilentlyContinue
    [pscustomobject]@{
        Windows = $product
        RdpEnabled = (Get-ItemPropertyValue -Path $rdpPath -Name fDenyTSConnections) -eq 0
        NetworkLevelAuthentication = (Get-ItemPropertyValue -Path $nlaPath -Name UserAuthentication) -eq 1
        FirewallScope = "Private profile; LocalSubnet only"
        Tcp3389Listening = [bool]$tcp
        InternetExposureConfigured = $false
        Note = "Do not configure router port forwarding. For remote networks, use a trusted VPN; never expose TCP 3389 directly."
    } | Format-List
}
