$ErrorActionPreference = "Stop"

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
