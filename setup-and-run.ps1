# One-time local Node setup and app runner for Windows
$nodeVersion = 'v20.15.1'
$nodeZip = "node-$nodeVersion-win-x64.zip"
$nodeUrl = "https://nodejs.org/dist/$nodeVersion/$nodeZip"
$toolsDir = "$PSScriptRoot\tools"
$nodeDir = "$toolsDir\node"
$zipPath = "$toolsDir\$nodeZip"

if (-not (Test-Path $nodeDir)) {
    Write-Host 'Downloading portable Node.js...'
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    Invoke-WebRequest -Uri $nodeUrl -OutFile $zipPath -UseBasicParsing
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    $extracted = Get-ChildItem -Path $toolsDir -Filter "node-$nodeVersion-win-x64" -Directory
    if ($extracted) { Rename-Item -Path $extracted.FullName -NewName 'node' -Force }
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
}

$npm = Join-Path $nodeDir 'npm.cmd'
$node = Join-Path $nodeDir 'node.exe'

if (-not (Test-Path "$PSScriptRoot\node_modules")) {
    Write-Host 'Installing npm dependencies...'
    & $npm install
}

Write-Host 'Starting IT Support Portal on http://localhost:3000'
& $node app.js
