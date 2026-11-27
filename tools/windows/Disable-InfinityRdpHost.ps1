[CmdletBinding(SupportsShouldProcess=$true)]
param()
$ErrorActionPreference = "Stop"
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw "Open Windows PowerShell as Administrator." }
$rdpPath = "HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server"
if ($PSCmdlet.ShouldProcess("This Windows PC", "Disable Remote Desktop and built-in Remote Desktop firewall rules")) {
    Set-ItemProperty -Path $rdpPath -Name fDenyTSConnections -Value 1 -Type DWord
    Get-NetFirewallRule -Name "RemoteDesktop-UserMode-In-TCP","RemoteDesktop-UserMode-In-UDP" -ErrorAction SilentlyContinue | Disable-NetFirewallRule
    [pscustomobject]@{
        RemoteDesktopDisabled = (Get-ItemPropertyValue -Path $rdpPath -Name fDenyTSConnections) -eq 1
        FirewallRulesDisabled = $true
        Note = "Existing VPN configuration and unrelated firewall rules were not changed."
    } | Format-List
}
