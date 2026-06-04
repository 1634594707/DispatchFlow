# Amap driving route smoke test (ZJF-PICK-01 -> ZJF-DROP-01)
# Usage:
#   $env:FSD_AMAP_WEB_SERVICE_KEY = "your_web_service_key"
#   .\scripts\dev\test_amap_route.ps1
# Then restart backend (back/run-dev.ps1) and re-run.

param(
    [string]$Base = "http://localhost:8080",
    [string]$WebServiceKey = "",
    [string]$Origin = "121.074453,31.960396",
    [string]$Destination = "121.079762,31.963627"
)

$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

function Read-DotEnvValue {
    param([string]$Name)
    $dotEnv = Join-Path $RepoRoot ".env"
    if (-not (Test-Path $dotEnv)) { return $null }
    foreach ($line in Get-Content $dotEnv) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { continue }
        if ($Matches[1] -eq $Name) { return $Matches[2].Trim().Trim('"').Trim("'") }
    }
    return $null
}

if (-not $WebServiceKey) { $WebServiceKey = $env:FSD_AMAP_WEB_SERVICE_KEY }
if (-not $WebServiceKey) { $WebServiceKey = Read-DotEnvValue "FSD_AMAP_WEB_SERVICE_KEY" }

function Get-ApiErrorBody {
    param($ErrorRecord)
    $resp = $ErrorRecord.Exception.Response
    if (-not $resp) { return $ErrorRecord.Exception.Message }
    try {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        if ($body) { return $body }
    } catch { }
    return $ErrorRecord.Exception.Message
}

Write-Host "=== Amap Web Service Key (direct API) ===" -ForegroundColor Cyan
if (-not $WebServiceKey) {
    Write-Host "No key in env or $RepoRoot\.env (backend may still have key if run-dev.ps1 loaded it)." -ForegroundColor Yellow
    Write-Host "  Add FSD_AMAP_WEB_SERVICE_KEY to .env (Web Service key, not VITE_AMAP_KEY)" -ForegroundColor Gray
} else {
    if (-not $env:FSD_AMAP_WEB_SERVICE_KEY) {
        Write-Host "Key loaded from .env (current shell env was empty)" -ForegroundColor DarkGray
    }
    $masked = if ($WebServiceKey.Length -gt 8) { $WebServiceKey.Substring(0, 4) + "..." } else { "***" }
    Write-Host "Key present: $masked" -ForegroundColor Gray
    $url = "https://restapi.amap.com/v3/direction/driving?key=$([uri]::EscapeDataString($WebServiceKey))&origin=$Origin&destination=$Destination&extensions=base"
    try {
        $raw = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 15
        if ($raw.status -eq "1") {
            $path = $raw.route.paths[0]
            $steps = @($path.steps).Count
            Write-Host "Amap API OK: distance=$($path.distance)m steps=$steps" -ForegroundColor Green
        } else {
            Write-Host "Amap API error: status=$($raw.status) info=$($raw.info)" -ForegroundColor Red
            if ($raw.info -match "USERKEY|INVALID") {
                Write-Host "  Use Web服务 key, not front VITE_AMAP_KEY (JS API)." -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "Amap HTTP failed: $(Get-ApiErrorBody $_)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== Backend road-route (after restart with key) ===" -ForegroundColor Cyan

$loginBody = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
$token = $null
try {
    $r = Invoke-WebRequest -Uri "$Base/api/admin/auth/login" -Method POST `
        -ContentType "application/json" -Body $loginBody -UseBasicParsing -TimeoutSec 10
    $token = $r.Headers["X-Admin-Token"]
    if (-not $token) { $token = ($r.Content | ConvertFrom-Json).data.token }
} catch {
    Write-Host "Backend login failed (is run-dev.ps1 running?): $(Get-ApiErrorBody $_)" -ForegroundColor Red
    exit 1
}

try {
    $health = Invoke-RestMethod -Uri "$Base/api/admin/park/road-route/health" `
        -Headers @{ "X-Admin-Token" = $token } -Method GET
    $health.data | Format-List
    if (-not $health.data.amapDriving) {
        Write-Host "amapDriving=false: set FSD_AMAP_WEB_SERVICE_KEY and restart backend." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Health failed: $(Get-ApiErrorBody $_)" -ForegroundColor Red
}

$partsO = $Origin -split ","
$partsD = $Destination -split ","
$validateBody = @{
    originLng = [decimal]$partsO[0]
    originLat = [decimal]$partsO[1]
    destinationLng = [decimal]$partsD[0]
    destinationLat = [decimal]$partsD[1]
} | ConvertTo-Json

Write-Host ""
Write-Host "=== Plan route (ChainedRoadRouteService) ===" -ForegroundColor Cyan
try {
    $vr = Invoke-RestMethod -Uri "$Base/api/admin/park/road-route/validate" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "X-Admin-Token" = $token } `
        -Body $validateBody
    $vr.data | Format-List
    $src = $vr.data.source
    if ($src -eq "AMAP") {
        Write-Host "Route source: AMAP (Gaode driving polyline in use)" -ForegroundColor Green
    } elseif ($src -eq "LOCAL_GRAPH") {
        Write-Host "Route source: LOCAL_GRAPH (Amap off, rejected, or sparse graph)" -ForegroundColor Yellow
    } else {
        Write-Host "Route source: $src" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Validate failed: $(Get-ApiErrorBody $_)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Tip: place a new order after AMAP is on; vehicle polyline uses the same planner." -ForegroundColor DarkGray
