param(
    [switch]$Configure,
    [switch]$InitializeOnly,
    [switch]$Serve,
    [string]$Model = 'gpt-5.6-terra'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsDir = Join-Path $env:LOCALAPPDATA 'PaynotesTesting'
$settingsFile = Join-Path $settingsDir 'openai.xml'
New-Item -ItemType Directory -Force -Path $settingsDir | Out-Null
if (Test-Path -LiteralPath $settingsFile) {
    $settings = Import-Clixml -LiteralPath $settingsFile
} else {
    $randomBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $settings = [pscustomobject]@{
        Model = $Model
        ApiKey = $null
        PilotToken = (ConvertTo-SecureString ([Convert]::ToBase64String($randomBytes)) -AsPlainText -Force)
    }
}
if ($Configure) {
    $settings.ApiKey = Read-Host 'OpenAI project API key (hidden; never paste it into chat)' -AsSecureString
    if ($settings.ApiKey.Length -eq 0) { throw 'No API key entered. Existing configuration was not changed.' }
    $settings.Model = $Model
}
# Windows Export-Clixml encrypts SecureString values using this user's DPAPI identity.
$settings | Export-Clixml -LiteralPath $settingsFile
if ($InitializeOnly) {
    Write-Host 'Private app test token initialized in the Windows-encrypted local store. This is not an OpenAI API key.'
    return
}
$previousKey = $env:OPENAI_API_KEY
$previousModel = $env:OPENAI_MODEL
$previousToken = $env:PILOT_ACCESS_TOKEN
try {
    if (-not $env:OPENAI_API_KEY -and $settings.ApiKey) {
        $env:OPENAI_API_KEY = [Net.NetworkCredential]::new('', $settings.ApiKey).Password
    }
    $localEnv = Join-Path $projectRoot '.env.local'
    if (-not $env:OPENAI_API_KEY -and -not (Test-Path -LiteralPath $localEnv)) { throw 'OpenAI project access is not configured. Complete the secure OpenAI Developers key setup first.' }
    $env:OPENAI_MODEL = if ($previousModel) { $previousModel } else { $settings.Model }
    $env:PILOT_ACCESS_TOKEN = [Net.NetworkCredential]::new('', $settings.PilotToken).Password
    Push-Location $projectRoot
    try {
        $entry = if ($Serve) { 'server/insights.cjs' } else { 'scripts/test-openai.cjs' }
        if (Test-Path -LiteralPath $localEnv) { & node --env-file=$localEnv $entry }
        else { & node $entry }
        if ($LASTEXITCODE -ne 0) { throw 'OpenAI test/service did not complete successfully. See the non-secret status above.' }
    } finally { Pop-Location }
} finally {
    $env:OPENAI_API_KEY = $previousKey
    $env:OPENAI_MODEL = $previousModel
    $env:PILOT_ACCESS_TOKEN = $previousToken
}
