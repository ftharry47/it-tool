param(
    [string]$AzureClientId = $env:VITE_AZURE_CLIENT_ID,
    [string]$AzureTenantId = $env:VITE_AZURE_TENANT_ID,
    [string]$AppBaseUrl = $env:VITE_APP_BASE_URL
)

$ErrorActionPreference = "Stop"

if (-not $AzureClientId) {
    throw "VITE_AZURE_CLIENT_ID is required. Pass -AzureClientId or set the VITE_AZURE_CLIENT_ID environment variable."
}
if (-not $AzureTenantId) {
    throw "VITE_AZURE_TENANT_ID is required. Pass -AzureTenantId or set the VITE_AZURE_TENANT_ID environment variable."
}
if (-not $AppBaseUrl) {
    throw "VITE_APP_BASE_URL is required. Pass -AppBaseUrl or set the VITE_APP_BASE_URL environment variable."
}

$env:VITE_AZURE_CLIENT_ID = $AzureClientId
$env:VITE_AZURE_TENANT_ID = $AzureTenantId
$env:VITE_APP_BASE_URL = $AppBaseUrl

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Error "Maven (mvn) was not found on PATH. Install Maven and try again."
}

# Avoid `mvn clean` deleting target/node/node_modules while it is still locked by the
# frontend-maven plugin / Windows. `mvn package` recompiles and repackages everything
# that matters; we only remove the final artifacts so the zip step is deterministic.
$artifacts = @("target/app.jar", "target/app.jar.original", "target/app.zip")
foreach ($a in $artifacts) {
    if (Test-Path $a) { Remove-Item $a -Force }
}

& mvn package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }

$zipPath = "target/app.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath }

Compress-Archive -Path "target/app.jar", "startup.sh" -DestinationPath $zipPath -Force
Write-Host "Created $zipPath"
