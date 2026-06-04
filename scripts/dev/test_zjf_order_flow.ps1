# ZJF L1 order + route smoke test (Docker MySQL + backend on :8080)
# Usage: .\scripts\dev\test_zjf_order_flow.ps1

$Base = "http://localhost:8080"
$MobileKey = $env:FSD_MOBILE_DEMO_API_KEY
if (-not $MobileKey) { $MobileKey = "ZJF-MOBILE-DEMO-2026" }

function Get-ApiErrorBody {
    param($ErrorRecord)
    $resp = $ErrorRecord.Exception.Response
    if (-not $resp) { return $ErrorRecord.Exception.Message }
    try {
        $stream = $resp.GetResponseStream()
        if (-not $stream) { return $ErrorRecord.Exception.Message }
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        $reader.Close()
        if ($body) { return $body }
    } catch { }
    return "$($ErrorRecord.Exception.Message) (HTTP $($resp.StatusCode.value__))"
}

function Get-AdminToken {
    $loginBody = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
    try {
        $r = Invoke-WebRequest -Uri "$Base/api/admin/auth/login" -Method POST `
            -ContentType "application/json" -Body $loginBody -UseBasicParsing
        $token = $r.Headers["X-Admin-Token"]
        if ($token) { return $token }
        ($r.Content | ConvertFrom-Json).data.token
    } catch {
        return $null
    }
}

$AdminToken = Get-AdminToken
if ($AdminToken) {
    Write-Host "Admin login OK (health check enabled)" -ForegroundColor DarkGray
} else {
    Write-Host "Admin login skipped (set FSD_ADMIN_AUTH_ENABLED=false or use admin/admin123)" -ForegroundColor Yellow
}

Write-Host "=== 1. Road route health ===" -ForegroundColor Cyan
if ($AdminToken) {
    try {
        $health = Invoke-RestMethod -Uri "$Base/api/admin/park/road-route/health" `
            -Headers @{ "X-Admin-Token" = $AdminToken } -Method GET
        $health.data | Format-List
    } catch {
        Write-Host "Health failed: $(Get-ApiErrorBody $_)" -ForegroundColor Yellow
    }
} else {
    Write-Host "Skipped (no admin token)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== 2. Mobile API key (Docker MySQL) ===" -ForegroundColor Cyan
$keyRow = docker exec fsd-mysql mysql -uroot -proot -N fsd_core -e `
    "SELECT COUNT(*) FROM t_external_api_key WHERE api_key='$MobileKey' AND status='ACTIVE' AND deleted=0;" 2>$null
if ($keyRow -and ($keyRow.Trim() -eq "1")) {
    Write-Host "Key OK: $MobileKey" -ForegroundColor Gray
} else {
    Write-Host "Key missing. Run: docker exec fsd-mysql mysql ... < back/sql/migrations/V25__zjf_mobile_demo_api_key.sql" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== 3. Station IDs ===" -ForegroundColor Cyan
docker exec fsd-mysql mysql -uroot -proot -N fsd_core -e `
    "SELECT id, station_code, coord_lng, coord_lat FROM t_station WHERE station_code IN ('ZJF-PICK-01','ZJF-DROP-01') AND deleted=0;" 2>$null

$pickId = (docker exec fsd-mysql mysql -uroot -proot -N fsd_core -e `
    "SELECT id FROM t_station WHERE station_code='ZJF-PICK-01' AND deleted=0 LIMIT 1;" 2>$null).Trim()
$dropId = (docker exec fsd-mysql mysql -uroot -proot -N fsd_core -e `
    "SELECT id FROM t_station WHERE station_code='ZJF-DROP-01' AND deleted=0 LIMIT 1;" 2>$null).Trim()

if (-not $pickId -or -not $dropId) {
    Write-Host "Stations not found. Run Flyway migrations first." -ForegroundColor Red
    exit 1
}
Write-Host "PICK-01 id=$pickId  DROP-01 id=$dropId" -ForegroundColor Gray

Write-Host ""
Write-Host "=== 4. Mobile order (PICK-01 -> DROP-01) ===" -ForegroundColor Cyan
Write-Host "If this fails with ADMIN_AUTH_REQUIRED, restart backend after pulling interceptor fix." -ForegroundColor DarkGray

$body = @{
    parkId = 1
    pickupStationId = [long]$pickId
    dropoffStationId = [long]$dropId
    priority = "P2"
    remark = "dev route test"
} | ConvertTo-Json

try {
    $order = Invoke-RestMethod -Uri "$Base/api/admin/park/orders" -Method POST `
        -ContentType "application/json" `
        -Headers @{ "X-Mobile-Api-Key" = $MobileKey } `
        -Body $body
    $order.data | Format-List
} catch {
    Write-Host "Order failed:" -ForegroundColor Red
    Write-Host (Get-ApiErrorBody $_) -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Done. Open http://localhost:5173/mobile/order or /vehicle-tracking to check the route." -ForegroundColor Green
