#Requires -Version 5.1
<#
.SYNOPSIS
  Deploy the IT Support Portal to Azure App Service.
.DESCRIPTION
  Creates an Azure resource group, App Service plan, Linux Web App,
  builds a zip of the application, and deploys it.
  Run from the project root in PowerShell.
.PARAMETER ResourceGroup
  Azure resource group name.
.PARAMETER PlanName
  App Service plan name.
.PARAMETER AppName
  Web app name (must be globally unique).
.PARAMETER Location
  Azure region, e.g. eastus.
#>
param(
    [string]$ResourceGroup = 'it-support-rg',
    [string]$PlanName = 'it-support-plan',
    [string]$AppName = 'it-support-portal',
    [string]$Location = 'eastus',
    [string]$ZipFile = 'tools\deploy.zip'
)

$ErrorActionPreference = 'Stop'

function Test-Command($cmd) {
    return [bool](Get-Command $cmd -ErrorAction SilentlyContinue)
}

if (-not (Test-Command 'az')) {
    Write-Error 'Azure CLI (az) is not installed. Install from https://aka.ms/installazurecliwindows'
}

Write-Host 'Checking Azure login status...'
$account = az account show 2>$null | ConvertFrom-Json
if (-not $account) {
    Write-Host 'Not logged in. Running az login...'
    az login
}

Write-Host 'Creating resource group...'
az group create --name $ResourceGroup --location $Location | Out-Null

Write-Host 'Creating App Service plan...'
az appservice plan create --name $PlanName --resource-group $ResourceGroup --sku B1 --is-linux | Out-Null

Write-Host 'Creating Web App...'
az webapp create --resource-group $ResourceGroup --plan $PlanName --name $AppName --runtime 'NODE|18-lts' | Out-Null

Write-Host 'Installing dependencies...'
if (-not (Test-Path 'package-lock.json')) {
    npm install
} else {
    npm ci
}

Write-Host 'Removing old zip if present...'
if (Test-Path $ZipFile) { Remove-Item $ZipFile -Force }

Write-Host 'Building deployment zip...'
$items = 'app.js','package.json','package-lock.json','public','src'
Compress-Archive -Path $items -DestinationPath $ZipFile -Force

Write-Host 'Deploying zip to Azure...'
az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $ZipFile

Write-Host 'Configuring app settings...'
az webapp config appsettings set --resource-group $ResourceGroup --name $AppName --settings SCM_DO_BUILD_DURING_DEPLOYMENT=true | Out-Null

Write-Host 'Restarting app...'
az webapp restart --resource-group $ResourceGroup --name $AppName

Write-Host "Deployment complete. Browse to: https://$AppName.azurewebsites.net"
Write-Host 'Stream logs with: az webapp log tail --resource-group' $ResourceGroup '--name' $AppName
