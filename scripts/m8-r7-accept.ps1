# M8-R7 road-following acceptance (API automation)
# Docs: docs/v3/M8-R7-ACCEPTANCE.md
#
# Usage:
#   .\scripts\m8-r7-accept.ps1
#   .\scripts\m8-r7-accept.ps1 -SkipOrder
#   .\scripts\m8-r7-accept.ps1 -ExpectLocalGraphOnly
#   .\scripts\m8-r7-accept.ps1 -JsonReport

param(
    [string]$BaseUrl = $(if ($env:FSD_API_BASE) { $env:FSD_API_BASE } else { 'http://localhost:8080' }),
    [string]$Username = $(if ($env:FSD_ADMIN_USER) { $env:FSD_ADMIN_USER } else { 'admin' }),
    [string]$Password = $(if ($env:FSD_ADMIN_PASSWORD) { $env:FSD_ADMIN_PASSWORD } else { 'admin123' }),
    [string]$PickupCode = 'ZJF-PICK-01',
    [string]$DropoffCode = 'ZJF-DROP-01',
    [int]$MinVehicles = 4,
    [int]$MinRouteVertices = 4,
    [int]$OrderPollSeconds = 90,
    [int]$OrderPollIntervalSec = 3,
    [int]$TimeoutSec = 10,
    [switch]$ExpectLocalGraphOnly,
    [switch]$SkipOrder,
    [switch]$JsonReport,
    [string]$ReportPath = ''
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $ReportPath) {
    $ReportPath = Join-Path $Root 'dist\m8-r7-report.json'
}

$script:Results = New-Object System.Collections.Generic.List[object]
$script:FailCount = 0
$script:WarnCount = 0
$script:PassCount = 0
$script:AdminToken = $null

function Write-Title([string]$Text) {
    Write-Host ''
    Write-Host ('== {0} ==' -f $Text) -ForegroundColor Cyan
}

function Add-Result {
    param(
        [string]$Id,
        [string]$Name,
        [ValidateSet('PASS', 'FAIL', 'WARN', 'MANUAL')]
        [string]$Status,
        [string]$Detail
    )
    switch ($Status) {
        'PASS' { $color = 'Green'; $script:PassCount++ }
        'FAIL' { $color = 'Red'; $script:FailCount++ }
        'WARN' { $color = 'Yellow'; $script:WarnCount++ }
        'MANUAL' { $color = 'DarkGray' }
    }
    $icon = switch ($Status) {
        'PASS' { '[OK]  ' }
        'FAIL' { '[FAIL]' }
        'WARN' { '[WARN]' }
        'MANUAL' { '[人工]' }
    }
    Write-Host ('{0} {1} {2}' -f $icon, $Id, $Name) -ForegroundColor $color
    if ($Detail) { Write-Host ('       {0}' -f $Detail) -ForegroundColor DarkGray }
    $script:Results.Add([ordered]@{ id = $Id; name = $Name; status = $Status; detail = $Detail })
}

function Format-HttpError {
    param([System.Management.Automation.ErrorRecord]$Err)
    $msg = $Err.Exception.Message
    if ($msg -match 'timed out|timeout') {
        return ('连接 {0} 超时 ({1} 秒), 请确认后端已启动: back/run-dev.ps1' -f $BaseUrl, $TimeoutSec)
    }
    if ($msg -match 'connection|refused|actively refused|无法连接') {
        return ('无法连接 {0}, 请先启动后端' -f $BaseUrl)
    }
    return $msg
}

function Invoke-FsdApi {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        $Body = $null
    )
    $uri = $BaseUrl + $Path
    $headers = @{ 'Content-Type' = 'application/json' }
    if ($script:AdminToken) { $headers['X-Admin-Token'] = $script:AdminToken }

    $params = @{
        Uri = $uri
        Method = $Method
        Headers = $headers
        UseBasicParsing = $true
        TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
    try {
        $resp = Invoke-WebRequest @params
    }
    catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errBody = $reader.ReadToEnd()
            throw ('HTTP {0} {1}: {2}' -f $_.Exception.Response.StatusCode, $Path, $errBody)
        }
        throw
    }
    $json = $resp.Content | ConvertFrom-Json
    if (-not $json.success) {
        throw ('API {0} failed: {1} {2}' -f $Path, $json.code, $json.message)
    }
    return $json.data
}

function Get-PilotVehicles($vehicles) {
    return @($vehicles | Where-Object { $_.vehicleCode -like 'PARK-*' })
}

function Get-PolylineVertices($vehicle) {
    $route = $vehicle.plannedRouteGeo
    if ($route -and $route.Count -ge $MinRouteVertices) { return $route.Count }
    $trail = $vehicle.geoTrajectory
    if ($trail -and $trail.Count -ge $MinRouteVertices) { return $trail.Count }
    return 0
}

function Test-RouteQuality {
    param($vehicle, [string]$Context)
    $issues = @()
    $vertices = Get-PolylineVertices $vehicle
    if ($vertices -lt $MinRouteVertices) {
        $issues += ('顶点数={0} (少于 {1})' -f $vertices, $MinRouteVertices)
    }
    if ($vehicle.routeSource -eq 'STRAIGHT_LINE') {
        $issues += 'routeSource=STRAIGHT_LINE'
    }
    if ($vehicle.routeInvalid -eq $true) {
        $issues += 'routeInvalid=true'
    }
    $detail = if ($issues.Count) {
        ('{0}: {1}' -f $Context, ($issues -join '; '))
    }
    else {
        ('{0}: 顶点={1} source={2}' -f $Context, $vertices, $vehicle.routeSource)
    }
    return @{ ok = ($issues.Count -eq 0); detail = $detail }
}

function Show-Summary {
    Write-Title '汇总'
    $manualCount = @($script:Results | Where-Object { $_.status -eq 'MANUAL' }).Count
    $color = if ($script:FailCount -gt 0) { 'Red' } else { 'Green' }
    Write-Host ('通过: {0}  失败: {1}  警告: {2}  人工: {3}' -f $script:PassCount, $script:FailCount, $script:WarnCount, $manualCount) -ForegroundColor $color
    if ($JsonReport) {
        $dir = Split-Path $ReportPath -Parent
        if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        [ordered]@{
            timestamp = (Get-Date).ToString('o')
            baseUrl = $BaseUrl
            pass = $script:PassCount
            fail = $script:FailCount
            warn = $script:WarnCount
            results = $script:Results
        } | ConvertTo-Json -Depth 6 | Set-Content -Path $ReportPath -Encoding UTF8
        Write-Host ('报告: {0}' -f $ReportPath) -ForegroundColor DarkGray
    }
}

Write-Title ('M8-R7 贴路验收 - {0}' -f $BaseUrl)
Write-Host ('时间: {0}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))

Write-Title '认证'
try {
    $loginBody = @{ username = $Username; password = $Password }
    $loginResp = Invoke-WebRequest -Uri ($BaseUrl + '/api/admin/auth/login') -Method POST `
        -Body ($loginBody | ConvertTo-Json) -ContentType 'application/json' -UseBasicParsing -TimeoutSec $TimeoutSec
    $loginJson = $loginResp.Content | ConvertFrom-Json
    if (-not $loginJson.success) { throw $loginJson.message }
    $script:AdminToken = $loginJson.data.token
    if (-not $script:AdminToken -and $loginResp.Headers['X-Admin-Token']) {
        $script:AdminToken = $loginResp.Headers['X-Admin-Token']
    }
    if (-not $script:AdminToken) { throw '登录响应无 token' }
    Add-Result 'AUTH' '管理端登录' 'PASS' ('用户 {0}' -f $Username)
}
catch {
    Add-Result 'AUTH' '管理端登录' 'FAIL' (Format-HttpError $_)
    Show-Summary
    exit 1
}

Write-Title 'R7-4 道路路径 health'
try {
    $health = Invoke-FsdApi -Path '/api/admin/park/road-route/health'
    $routeOk = $health.amapDriving -or $health.localGraph
    if (-not $routeOk) {
        Add-Result 'R7-4' '路径能力可用' 'FAIL' ('amap/local 均为 false: {0}' -f $health.detail)
    }
    elseif ($ExpectLocalGraphOnly) {
        if ($health.localGraph -and -not $health.amapDriving) {
            Add-Result 'R7-4' 'LOCAL_GRAPH 模式' 'PASS' ('segments={0}' -f $health.localGraphSegments)
        }
        else {
            Add-Result 'R7-4' 'LOCAL_GRAPH 模式' 'FAIL' '请清空 FSD_AMAP_WEB_SERVICE_KEY 并重启后端'
        }
    }
    else {
        $mode = if ($health.amapDriving) { 'AMAP' } else { 'LOCAL_GRAPH' }
        Add-Result 'R7-4' '路径能力可用' 'PASS' ('{0} fallback={1} amapSuccess={2}' -f $mode, $health.fallbackCount, $health.amapSuccessCount)
        if ($health.fallbackCount -gt 0) {
            Add-Result 'R7-4b' '降级次数' 'WARN' ('fallbackCount={0}' -f $health.fallbackCount)
        }
    }
}
catch {
    Add-Result 'R7-4' '道路路径 health' 'FAIL' $_.Exception.Message
}

$pickup = $null
$dropoff = $null

Write-Title 'R7-8 路线防穿模 validate'
try {
    $stations = Invoke-FsdApi -Path '/api/admin/park/stations'
    $pickup = $stations | Where-Object { $_.stationCode -eq $PickupCode } | Select-Object -First 1
    $dropoff = $stations | Where-Object { $_.stationCode -eq $DropoffCode } | Select-Object -First 1
    if (-not $pickup -or -not $dropoff) {
        throw ('未找到站点 {0} / {1}, 请执行 Flyway V23/V24' -f $PickupCode, $DropoffCode)
    }
    $validateBody = @{
        originLng = [decimal]$pickup.coordLng
        originLat = [decimal]$pickup.coordLat
        destinationLng = [decimal]$dropoff.coordLng
        destinationLat = [decimal]$dropoff.coordLat
    }
    $vr = Invoke-FsdApi -Method POST -Path '/api/admin/park/road-route/validate' -Body $validateBody
    if ($vr.invalid -or $vr.crossesBuilding -or $vr.crossesRiver) {
        Add-Result 'R7-8' '试点线路 validate' 'FAIL' ('invalid={0} building={1} river={2} v={3} src={4}' -f $vr.invalid, $vr.crossesBuilding, $vr.crossesRiver, $vr.vertexCount, $vr.source)
    }
    elseif ($vr.vertexCount -lt $MinRouteVertices) {
        Add-Result 'R7-8' '试点线路 validate' 'FAIL' ('顶点 {0} 少于 {1} src={2}' -f $vr.vertexCount, $MinRouteVertices, $vr.source)
    }
    else {
        Add-Result 'R7-8' '试点线路 validate' 'PASS' ('{0} to {1} v={2} src={3}' -f $PickupCode, $DropoffCode, $vr.vertexCount, $vr.source)
    }
}
catch {
    Add-Result 'R7-8' '路线 validate' 'FAIL' $_.Exception.Message
}

Write-Title 'R7-1 短驳地理仿真车贴路'
try {
    $vehicles = @(Invoke-FsdApi -Path '/api/admin/park/vehicles')
    $pilot = Get-PilotVehicles $vehicles
    $online = @($pilot | Where-Object { $_.onlineStatus -ne 'OFFLINE' })
    if ($online.Count -lt $MinVehicles) {
        Add-Result 'R7-1a' '在线仿真车数量' 'FAIL' ('PARK 在线 {0} 少于 {1}' -f $online.Count, $MinVehicles)
    }
    else {
        Add-Result 'R7-1a' '在线仿真车数量' 'PASS' ('PARK 在线 {0} 台' -f $online.Count)
    }

    $busy = @($pilot | Where-Object { $_.dispatchStatus -eq 'BUSY' -or $_.currentTaskId })
    $bad = @()
    foreach ($v in $busy) {
        $q = Test-RouteQuality $v ('vehicle {0}' -f $v.vehicleCode)
        if (-not $q.ok) { $bad += $q.detail }
    }
    if ($busy.Count -eq 0) {
        $withRoute = @($pilot | Where-Object { (Get-PolylineVertices $_) -ge $MinRouteVertices -and $_.routeSource -ne 'STRAIGHT_LINE' })
        if ($withRoute.Count -ge 1) {
            Add-Result 'R7-1b' '计划路线质量' 'PASS' ('无 BUSY 车, {0} 台已有 geo 路线' -f $withRoute.Count)
        }
        else {
            Add-Result 'R7-1b' '计划路线质量' 'WARN' '建议执行 R7-2 下单或等待派单'
        }
    }
    elseif ($bad.Count -eq 0) {
        Add-Result 'R7-1b' '计划路线质量' 'PASS' ('已检查 {0} 台 BUSY 车' -f $busy.Count)
    }
    else {
        Add-Result 'R7-1b' '计划路线质量' 'FAIL' ($bad -join ' | ')
    }
}
catch {
    Add-Result 'R7-1' '车队快照' 'FAIL' $_.Exception.Message
}

if (-not $SkipOrder) {
    Write-Title ('R7-2 下单跟车 {0} to {1}' -f $PickupCode, $DropoffCode)
    try {
        if (-not $pickup -or -not $dropoff) {
            $stations = Invoke-FsdApi -Path '/api/admin/park/stations'
            $pickup = $stations | Where-Object { $_.stationCode -eq $PickupCode } | Select-Object -First 1
            $dropoff = $stations | Where-Object { $_.stationCode -eq $DropoffCode } | Select-Object -First 1
        }
        $orderBody = @{
            pickupStationId = $pickup.stationId
            dropoffStationId = $dropoff.stationId
            priority = 'P1'
            remark = ('M8-R7 {0}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
        }
        $created = Invoke-FsdApi -Method POST -Path '/api/admin/park/orders' -Body $orderBody
        Add-Result 'R7-2a' '创建短驳订单' 'PASS' ('orderId={0} orderNo={1}' -f $created.orderId, $created.orderNo)

        $deadline = (Get-Date).AddSeconds($OrderPollSeconds)
        $assigned = $false
        $routeOk = $false
        $assignedVehicleId = $null
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds $OrderPollIntervalSec
            $orders = @(Invoke-FsdApi -Path '/api/admin/park/orders')
            $snap = $orders | Where-Object { $_.orderId -eq $created.orderId } | Select-Object -First 1
            if ($snap -and $snap.vehicleId) {
                $assigned = $true
                $assignedVehicleId = $snap.vehicleId
                $vehicles = @(Invoke-FsdApi -Path '/api/admin/park/vehicles')
                $veh = $vehicles | Where-Object { $_.vehicleId -eq $snap.vehicleId } | Select-Object -First 1
                if ($veh) {
                    $q = Test-RouteQuality $veh ('track {0} stage={1}' -f $veh.vehicleCode, $snap.runtimeStage)
                    if ($q.ok) {
                        $routeOk = $true
                        Add-Result 'R7-2b' '指派车辆贴路' 'PASS' $q.detail
                        break
                    }
                }
            }
        }
        if (-not $assigned) {
            Add-Result 'R7-2b' '指派车辆贴路' 'FAIL' ('{0}s 内未分配到车辆' -f $OrderPollSeconds)
        }
        elseif (-not $routeOk) {
            Add-Result 'R7-2b' '指派车辆贴路' 'FAIL' '已派车但 plannedRouteGeo 未达贴路标准'
        }
        $vid = if ($assignedVehicleId) { $assignedVehicleId } elseif ($created.vehicleId) { $created.vehicleId } else { '' }
        $trackPath = '/vehicle-tracking?mode=geo&orderId=' + $created.orderId
        if ($vid) { $trackPath += '&vehicleId=' + $vid }
        Add-Result 'R7-2c' '大屏跟车链接' 'MANUAL' ('浏览器: {0}' -f $trackPath)
    }
    catch {
        Add-Result 'R7-2' '下单跟车' 'FAIL' $_.Exception.Message
    }
}
else {
    Add-Result 'R7-2' '下单跟车' 'MANUAL' '已 SkipOrder 跳过'
}

Write-Title 'R7-6 双地图站点分层'
try {
    $stations = @(Invoke-FsdApi -Path '/api/admin/park/stations')
    $zjf = @($stations | Where-Object { $_.area -eq 'ZJF' -or ($_.stationCode -like 'ZJF-*') })
    $park = @($stations | Where-Object { $_.area -ne 'ZJF' -and ($_.stationCode -notlike 'ZJF-*') })
    if ($zjf.Count -lt 2) {
        Add-Result 'R7-6a' 'ZJF 短驳站点' 'FAIL' ('ZJF 站点 {0} 个' -f $zjf.Count)
    }
    else {
        Add-Result 'R7-6a' 'ZJF 短驳站点' 'PASS' ('ZJF {0} 园区 {1}' -f $zjf.Count, $park.Count)
    }
    Add-Result 'R7-6b' '园区调度 UI' 'MANUAL' '/vehicle-tracking 默认园区调度: 无 ZJF 站点'
}
catch {
    Add-Result 'R7-6' '站点分层' 'FAIL' $_.Exception.Message
}

Write-Title '需人工确认'
Add-Result 'R7-5' '录屏 30s' 'MANUAL' '短驳地理: 无 pickup-dropoff 虚线直连'
Add-Result 'R7-7' '配置自检页' 'MANUAL' 'M10: JS/Web/Mobile Key (暂用 health 接口)'
if ($ExpectLocalGraphOnly) {
    Add-Result 'R7-3' '仅本地路网' 'PASS' 'ExpectLocalGraphOnly 已通过'
}
else {
    Add-Result 'R7-3' '仅本地路网' 'MANUAL' '清空 Web Key 后: -ExpectLocalGraphOnly'
}

Show-Summary

if ($script:FailCount -gt 0) {
    Write-Host ''
    Write-Host 'M8-R7 自动化验收未通过. 详见 docs/v3/M8-R7-ACCEPTANCE.md' -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host 'M8-R7 自动化项已通过; 请完成人工项后关闭 R7.' -ForegroundColor Green
exit 0
