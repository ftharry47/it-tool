#requires -Version 5.1
<#
.SYNOPSIS
    Basic load test for the incident create + query endpoints.

.DESCRIPTION
    Runs a simple loop of POST and GET requests against the incident API,
    printing response-time percentiles and error counts. The script is
    intentionally lightweight and uses standard Invoke-RestRequest on Windows.

.PARAMETER BaseUrl
    Base URL of the running ITSM API (default: http://localhost:8080).

.PARAMETER Token
    Bearer token (JWT) for an AGENT+ user. Required.

.PARAMETER Total
    Total number of request pairs (create + query) to run (default: 100).

.PARAMETER Warmup
    Number of warmup pairs before measurement (default: 10).
#>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory=$true)]
    [string]$Token,
    [int]$Total = 100,
    [int]$Warmup = 10
)

$ErrorActionPreference = "Stop"

$headers = @{
    "Authorization" = "Bearer $Token"
    "Content-Type"  = "application/json"
}

$createUrl = "$BaseUrl/api/incidents"
$queryUrl  = "$BaseUrl/api/v1/incidents?search=load"

$createBody = @{
    requesterEmail = "loadtest@alignedcardio.example"
    title          = "Load test incident"
    description    = "Created by load_test.ps1"
    category       = "Software"
    impact         = 3
    urgency        = 3
} | ConvertTo-Json -Compress

function Invoke-Timed {
    param([scriptblock]$Script)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $result = & $Script
    $sw.Stop()
    return [pscustomobject]@{
        ElapsedMs = $sw.Elapsed.TotalMilliseconds
        Status    = $result.Status
    }
}

function Send-Pair {
    $pair = @()

    $post = Invoke-Timed {
        $resp = Invoke-RestMethod -Uri $createUrl -Method Post -Headers $headers -Body $createBody -SkipHttpErrorCheck
        return @{ Status = $resp.BaseResponse.StatusCode }
    }
    $pair += $post

    $get = Invoke-Timed {
        $resp = Invoke-RestMethod -Uri $queryUrl -Method Get -Headers $headers -SkipHttpErrorCheck
        return @{ Status = $resp.BaseResponse.StatusCode }
    }
    $pair += $get

    return $pair
}

Write-Host "Warming up $Warmup pair(s)..." -ForegroundColor Cyan
1..$Warmup | ForEach-Object { Send-Pair | Out-Null }

Write-Host "Running $Total pair(s) (POST + GET each)..." -ForegroundColor Cyan
$measurements = @()
1..$Total | ForEach-Object {
    $pair = Send-Pair
    $measurements += $pair
    if ($_ % 10 -eq 0) { Write-Host "  completed $_ / $Total" }
}

$createTimes = $measurements | Where-Object { $_ -ne $null } | Select-Object -Index (0..($measurements.Count-1) | Where-Object { $_ % 2 -eq 0 }) | ForEach-Object { $_.ElapsedMs }
$queryTimes  = $measurements | Where-Object { $_ -ne $null } | Select-Object -Index (0..($measurements.Count-1) | Where-Object { $_ % 2 -eq 1 }) | ForEach-Object { $_.ElapsedMs }

$createErrors = ($createTimes | Where-Object { $_ -eq $null }).Count
$queryErrors  = ($queryTimes  | Where-Object { $_ -eq $null }).Count

function Get-Percentile {
    param($Values, $Percentile)
    $sorted = $Values | Sort-Object
    $index = [math]::Ceiling($Percentile / 100.0 * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return $sorted[$index]
}

Write-Host "`n=== RESULTS ===" -ForegroundColor Green
Write-Host "POST /api/incidents  - count: $($createTimes.Count), errors: $createErrors, p95: $([math]::Round((Get-Percentile $createTimes 95),2)) ms, p99: $([math]::Round((Get-Percentile $createTimes 99),2)) ms"
Write-Host "GET  /api/v1/incidents - count: $($queryTimes.Count), errors: $queryErrors, p95: $([math]::Round((Get-Percentile $queryTimes 95),2)) ms, p99: $([math]::Round((Get-Percentile $queryTimes 99),2)) ms"

Write-Host "`nN+1 / index detection: run with 'spring.jpa.show-sql=true' or check pg_stat_statements for repeated SELECTs and missing index usage." -ForegroundColor Yellow
