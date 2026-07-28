#Requires -Version 5.1
<#
.SYNOPSIS
  Bulk import employee emails into the IT Support Portal directory.
.DESCRIPTION
  Reads tools/users.csv and calls the /api/bulkImportDirectory endpoint.
  The CSV must contain an 'email' column. Name and employee ID are generated.
  Optional: set BULK_IMPORT_TOKEN app setting and $env:BULK_IMPORT_TOKEN before running.
#>
param(
    [string]$CsvFile = 'tools\users.csv',
    [string]$AppUrl = 'https://alignedcardio-it-portal-bge7gud8huhsazcd.canadacentral-01.azurewebsites.net',
    [int]$BatchSize = 100
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $CsvFile)) {
    Write-Error "CSV file not found: $CsvFile"
}

Write-Host "Reading $CsvFile..."
$lines = Get-Content $CsvFile | Where-Object { $_.Trim() -ne '' }
$header = $lines[0]
$columns = $header -split ',' | ForEach-Object { $_.Trim().Trim('"').ToLower() }
$emailIndex = $columns.IndexOf('email')
if ($emailIndex -lt 0) { $emailIndex = 0 } # fallback to first column

$emails = foreach ($line in $lines | Select-Object -Skip 1) {
    $parts = $line -split '(?<!"),(?!")' # naive split, assumes no embedded commas
    $email = $parts[$emailIndex].Trim().Trim('"')
    if ($email -and $email -match '@') { $email }
}

$emails = @($emails | Where-Object { $_ } | Sort-Object -Unique)
Write-Host "Found $($emails.Count) unique emails."

$token = $env:BULK_IMPORT_TOKEN
if (-not $token) { $token = '' }

$uri = "$AppUrl/api/bulkImportDirectory"
$total = $emails.Count
$added = 0
$skipped = 0

for ($i = 0; $i -lt $total; $i += $BatchSize) {
    $batch = $emails[$i..([Math]::Min($i + $BatchSize - 1, $total - 1))]
    $body = @{ args = @($batch, $token) } | ConvertTo-Json -Depth 3
    try {
        $response = Invoke-RestMethod -Uri $uri -Method Post -ContentType 'application/json' -Body $body
        if ($response.success) {
            $added += $response.added
            $skipped += $response.skipped
            Write-Host "Batch $([Math]::Floor($i/$BatchSize) + 1): added $($response.added), skipped $($response.skipped)"
        } else {
            Write-Warning "Batch failed: $($response.error)"
        }
    } catch {
        Write-Warning "Batch error: $_"
    }
}

Write-Host "Import complete. Added $added, skipped $skipped of $total."
