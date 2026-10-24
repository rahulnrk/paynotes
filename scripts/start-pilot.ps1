param([int]$Port=4173)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$logRoot=Join-Path $projectRoot 'artifacts/pilot-service'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
try {
    $health=Invoke-RestMethod "http://127.0.0.1:$Port/health" -TimeoutSec 2
    if($health.ok){Write-Host "Pilot already available: http://127.0.0.1:$Port"; return}
} catch {}
$env:PORT=[string]$Port
$process=Start-Process -FilePath (Get-Command node).Source -ArgumentList @('admin/serve.cjs') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'stdout.log') -RedirectStandardError (Join-Path $logRoot 'stderr.log') -PassThru
$process.Id | Set-Content (Join-Path $logRoot 'service.pid')
Write-Host "Started pilot process $($process.Id). Open http://127.0.0.1:$Port"
Write-Host "Credentials: $logRoot/secrets.json"
Write-Host 'For remote phones, run ngrok http 4173 in your terminal and use its HTTPS address in the app.'
