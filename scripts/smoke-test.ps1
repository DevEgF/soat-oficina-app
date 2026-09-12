[CmdletBinding()]
param(
    [Parameter(Mandatory)][uri]$ApiUrl,
    [switch]$AllowLocal
)
$ErrorActionPreference = 'Stop'
if ($ApiUrl.Scheme -ne 'https' -and -not ($AllowLocal -and $ApiUrl.IsLoopback)) {
    throw 'Smoke URL must use HTTPS, except an explicitly allowed loopback URL'
}
$health = $ApiUrl.AbsoluteUri.TrimEnd('/') + '/actuator/health'
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $response = Invoke-RestMethod -Uri $health -TimeoutSec 10 -MaximumRedirection 0
        if ($response.status -eq 'UP') { Write-Output 'Application health UP'; return }
    } catch { }
    Start-Sleep -Seconds 2
}
throw 'Application health smoke failed'
