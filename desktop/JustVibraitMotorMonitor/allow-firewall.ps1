# Requires administrator rights: the script re-launches itself elevated.
$ruleName = 'JustVibrait Motor Monitor (UDP 45454)'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host 'Requesting administrator rights...'
    Start-Process powershell -Verb RunAs -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', ('"' + $PSCommandPath + '"')
    )
    exit
}

$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host ('Firewall rule already exists: ' + $ruleName)
    exit 0
}

New-NetFirewallRule `
    -DisplayName $ruleName `
    -Direction Inbound `
    -Protocol UDP `
    -LocalPort 45454 `
    -Action Allow `
    -Profile Private, Domain, Public | Out-Null

Write-Host ('Firewall rule created: ' + $ruleName)
