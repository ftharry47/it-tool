#Requires -Version 5.1
<#
.SYNOPSIS
  Upload the local db.json to the production Azure Web App.
.DESCRIPTION
  Uses the Kudu VFS REST API to copy the local it-tool db.json
  to /home/site/data/it-tool/db.json on the Azure App Service.
  Then restarts the web app.
  Run from the project root in PowerShell.
.PARAMETER ResourceGroup
  Azure resource group name.
.PARAMETER AppName
  Web app name (must be globally unique).
.PARAMETER LocalDbPath
  Full path to the local db.json file.
#>
param(
    [string]$ResourceGroup = 'it-support-rg',
    [string]$AppName = 'alignedcardio-it-portal-bge7gud8huhsazcd',
    [string]$LocalDbPath = 'C:\Users\SriHariThangavel\AppData\Local\it-tool\db.json'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $LocalDbPath)) {
    Write-Error "Local db.json not found at: $LocalDbPath"
}

$required = @('az')
foreach ($cmd in $required) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Error "Azure CLI ($cmd) is not installed. Install from https://aka.ms/installazurecliwindows"
    }
}

Write-Host "Checking Azure login status..."
$account = az account show 2>$null | ConvertFrom-Json
if (-not $account) {
    Write-Host "Not logged in. Running az login..."
    az login
}

Write-Host "Getting deployment credentials for $AppName..."
$credsJson = az webapp deployment list-publishing-credentials `
    --resource-group $ResourceGroup `
    --name $AppName `
    --query '{user:publishingUserName, pass:publishingPassword}' `
    -o json

if (-not $credsJson) {
    Write-Error "Could not get deployment credentials. Check the app name and resource group."
}

$creds = $credsJson | ConvertFrom-Json
$username = $creds.user
$password = $creds.pass

# Ensure DB_PATH points to the file we are uploading
$expectedDbPath = '/home/site/data/it-tool/db.json'
Write-Host "Setting DB_PATH app setting to $expectedDbPath ..."
az webapp config appsettings set `
    --resource-group $ResourceGroup `
    --name $AppName `
    --settings "DB_PATH=$expectedDbPath" | Out-Null

$pair = "$username`:$password"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($pair)
$base64 = [System.Convert]::ToBase64String($bytes)

$headers = @{
    'Authorization' = "Basic $base64"
    'If-Match'      = '*'
}

$remoteUrl = "https://$AppName.scm.azurewebsites.net/api/vfs/site/data/it-tool/db.json"
$remoteBackupUrl = "https://$AppName.scm.azurewebsites.net/api/vfs/site/data/it-tool/db.json.bak"

# Try to download remote db, back it up, and merge any new tickets before overwriting
Write-Host "Checking for new tickets on the remote db..."
try {
    $remoteText = Invoke-RestMethod -Uri $remoteUrl -Method Get -Headers $headers
    $remoteDb = $remoteText | ConvertFrom-Json
    $remoteCount = $remoteDb.tickets.Count
    Write-Host "Remote db has $remoteCount ticket(s)."

    # Back up the current remote db
    $backupTemp = [System.IO.Path]::GetTempFileName()
    $remoteText | Set-Content -Path $backupTemp -NoNewline -Encoding UTF8
    try {
        $null = Invoke-RestMethod -Uri $remoteBackupUrl -Method Put -Headers $headers -InFile $backupTemp -ContentType 'application/json'
        Write-Host "Remote db backed up to db.json.bak"
    } catch { Write-Host "Could not back up remote db: $_" }
    Remove-Item $backupTemp

    # Merge new tickets/notes/history from remote into local
    $localText = Get-Content $LocalDbPath -Raw -Encoding UTF8
    $localDb = $localText | ConvertFrom-Json
    if (-not $localDb.notes) { $localDb | Add-Member -NotePropertyName notes -NotePropertyValue @() -Force }
    if (-not $localDb.history) { $localDb | Add-Member -NotePropertyName history -NotePropertyValue @() -Force }

    $localTicketIds = @($localDb.tickets | ForEach-Object { $_.'Ticket ID' })
    $merged = 0
    foreach ($t in $remoteDb.tickets) {
        if ($localTicketIds -notcontains $t.'Ticket ID') {
            $localDb.tickets += $t
            $merged++
        }
    }

    if ($merged -gt 0) {
        $originalTicketIds = $localTicketIds
        foreach ($n in $remoteDb.notes) {
            if ($n.ticketId -and ($originalTicketIds -notcontains $n.ticketId)) { $localDb.notes += $n }
        }
        foreach ($h in $remoteDb.history) {
            if ($h.ticketId -and ($originalTicketIds -notcontains $h.ticketId)) { $localDb.history += $h }
        }
        $localDb | ConvertTo-Json -Depth 100 -Compress:$false | Set-Content -Path $LocalDbPath -NoNewline -Encoding UTF8
        Write-Host "Merged $merged new ticket(s) from remote. Local db now has $($localDb.tickets.Count) tickets."
    } else {
        Write-Host "No new tickets to merge."
    }
} catch { Write-Host "Could not download/merge remote db: $_" }

Write-Host "Uploading $LocalDbPath to $remoteUrl ..."
try {
    $null = Invoke-RestMethod `
        -Uri $remoteUrl `
        -Method Put `
        -Headers $headers `
        -InFile $LocalDbPath `
        -ContentType 'application/json'
    Write-Host "Upload successful."
} catch {
    Write-Error "Upload failed: $_"
}

Write-Host "Restarting web app..."
az webapp restart --resource-group $ResourceGroup --name $AppName

Write-Host "Done. The dashboard should now show the imported tickets at:"
Write-Host "  https://$AppName.azurewebsites.net/dashboard.html"
