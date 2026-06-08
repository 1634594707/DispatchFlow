<#
.SYNOPSIS
  V5-W3 test data generator - creates test orders/tasks for batch cancel testing
.PARAMETER Token
  Admin token from browser localStorage fsd_admin_token
.PARAMETER BaseUrl
  API base URL, default http://localhost:8080/api/admin
#>

param(
  [Parameter(Mandatory = $true)]
  [string]$Token,

  [string]$BaseUrl = "http://localhost:8080/api/admin"
)

$ErrorActionPreference = "Stop"
$headers = @{
  "X-Admin-Token" = $Token
  "Content-Type"  = "application/json"
}

Write-Host "=== V5-W3 test data generator ===" -ForegroundColor Cyan

# 1. Get parks
try {
  $parks = (Invoke-RestMethod -Uri "$BaseUrl/parks" -Headers $headers -Method Get).data
} catch {
  Write-Host "FAILED to fetch parks: $_" -ForegroundColor Red
  Write-Host "Check backend running and token correct" -ForegroundColor Yellow
  exit 1
}

if (-not $parks -or $parks.Count -eq 0) {
  Write-Host "No parks found" -ForegroundColor Red
  exit 1
}

$park = $parks[0]
Write-Host "Using park: $($park.parkCode) - $($park.parkName)" -ForegroundColor Green

# 2. Get stations
$stations = (Invoke-RestMethod -Uri "$BaseUrl/park/stations?parkId=$($park.parkId)" -Headers $headers -Method Get).data
if (-not $stations -or $stations.Count -lt 2) {
  Write-Host "Not enough stations (need >= 2)" -ForegroundColor Red
  exit 1
}

Write-Host "Available stations ($($stations.Count) total):"
$stations | ForEach-Object { Write-Host "  ID=$($_.stationId) $($_.stationName) ($($_.stationType))" -ForegroundColor Gray }

# Pick first pickup and first dropoff station
$pickup = $stations | Where-Object { $_.stationType -eq 'PICKUP' -or $_.stationType -eq 'ENTRY' } | Select-Object -First 1
$dropoff = $stations | Where-Object { $_.stationType -eq 'DROPOFF' -or $_.stationType -eq 'EXIT' -or $_.stationType -eq 'GENERAL' } | Select-Object -First 1

if (-not $pickup) { $pickup = $stations[0] }
if (-not $dropoff) { $dropoff = $stations[1] }

Write-Host "`nPickup: $($pickup.stationName) (ID=$($pickup.stationId))" -ForegroundColor Yellow
Write-Host "Dropoff: $($dropoff.stationName) (ID=$($dropoff.stationId))" -ForegroundColor Yellow

# 3. Create test orders (each generates a DispatchTask)
$testOrders = @(
  @{ priority = "P1"; remark = "V5-TEST-URGENT-1" },
  @{ priority = "P1"; remark = "V5-TEST-URGENT-2" },
  @{ priority = "P2"; remark = "V5-TEST-NORMAL-1" },
  @{ priority = "P2"; remark = "V5-TEST-NORMAL-2" },
  @{ priority = "P3"; remark = "V5-TEST-LOW-1" },
  @{ priority = "P3"; remark = "V5-TEST-LOW-2" }
)

$createdTaskIds = @()

for ($i = 0; $i -lt $testOrders.Length; $i++) {
  $order = $testOrders[$i]
  $body = @{
    parkId           = $park.parkId
    pickupStationId  = $pickup.stationId
    dropoffStationId = $dropoff.stationId
    priority         = $order.priority
    remark           = $order.remark
    externalOrderNo  = "TEST-BATCH-$(Get-Date -Format 'yyyyMMddHHmmss')-$i"
  } | ConvertTo-Json

  try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/park/orders" -Headers $headers -Method Post -Body $body
    Write-Host "[$($i+1)/6] OK $($order.remark) -> taskId=$($resp.data.taskId)" -ForegroundColor Green
    $createdTaskIds += $resp.data.taskId
  } catch {
    Write-Host "[$($i+1)/6] FAILED $($order.remark): $_" -ForegroundColor Red
  }

  Start-Sleep -Seconds 1
}

# 4. Summary
Write-Host "`n=== Ready ===" -ForegroundColor Cyan
if ($createdTaskIds.Count -gt 0) {
  Write-Host "Created $($createdTaskIds.Count) test tasks, IDs:" -ForegroundColor Green
  Write-Host ($createdTaskIds -join ", ") -ForegroundColor White
  Write-Host "`nNext steps:"
  Write-Host "  1. Refresh http://localhost:3000/workbench" -ForegroundColor Yellow
  Write-Host "  2. Select these tasks in the task pool" -ForegroundColor Yellow
  Write-Host "  3. Click batch cancel -> verify confirmation dialog" -ForegroundColor Yellow
} else {
  Write-Host "No tasks created, check backend logs" -ForegroundColor Red
}