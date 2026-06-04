# 编译全部模块并启动 fsd-bootstrap（避免园区等新接口 404）
Set-Location $PSScriptRoot
Write-Host "Building modules (clean install)..." -ForegroundColor Cyan
# -Dmaven.test.skip=true skips test-compile + surefire (dev startup only).
# -DskipTests still compiles integration tests and can fail before spring-boot:run.
mvn -pl fsd-bootstrap -am clean install "-Dmaven.test.skip=true"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

function Apply-Migration {
    param([string]$FileName)
    $localPath = Join-Path $PSScriptRoot "sql\migrations\$FileName"
    if (-not (Test-Path $localPath)) { return }
    docker cp $localPath "fsd-mysql:/tmp/$FileName" 2>$null
    if ($LASTEXITCODE -eq 0) {
        docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/$FileName" 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  applied $FileName" -ForegroundColor DarkGray
        }
    }
}

Write-Host "Applying legacy SQL migrations if MySQL container is running..." -ForegroundColor Cyan
Write-Host "  (V21+ managed by Flyway on startup — skipped here)" -ForegroundColor DarkGray
$mysqlRunning = docker ps --filter "name=fsd-mysql" --format "{{.Names}}" 2>$null
if ($mysqlRunning -eq "fsd-mysql") {
    Get-ChildItem (Join-Path $PSScriptRoot "sql\migrations\V*.sql") | Sort-Object Name | ForEach-Object {
        if ($_.Name -match '^V(\d+)__' -and [int]$Matches[1] -ge 21) {
            return
        }
        Apply-Migration $_.Name
    }
} else {
    Write-Host "  fsd-mysql not running — skip migrations (start docker compose first if needed)" -ForegroundColor Yellow
}

function Repair-FlywayV24Rename {
    if ($mysqlRunning -ne "fsd-mysql") { return }
    $v24Script = docker exec fsd-mysql mysql -uroot -proot -N -e `
        "SELECT script FROM flyway_schema_history WHERE version='24' LIMIT 1;" fsd_core 2>$null
    if (-not $v24Script) { return }
    $needsRepair = $v24Script -match 'zjf_mobile_demo_api_key'
    if (-not $needsRepair) {
        $checksum = docker exec fsd-mysql mysql -uroot -proot -N -e `
            "SELECT checksum FROM flyway_schema_history WHERE version='24' LIMIT 1;" fsd_core 2>$null
        if ($checksum -eq '783071413') { $needsRepair = $true }
    }
    if (-not $needsRepair) { return }
    Write-Host "Repairing Flyway V24/V25 (migration files were renamed)..." -ForegroundColor Yellow
    docker exec fsd-mysql mysql -uroot -proot fsd_core -e `
        "DELETE FROM flyway_schema_history WHERE version IN ('24','25');" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Cleared flyway_schema_history for V24/V25 — startup will apply coordinate fix + demo API key" -ForegroundColor DarkGray
    }
}

Repair-FlywayV24Rename

$repoRoot = Split-Path $PSScriptRoot -Parent
$dotEnv = Join-Path $repoRoot ".env"
if (Test-Path $dotEnv) {
    Get-Content $dotEnv | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { return }
        $name = $Matches[1]
        $val = $Matches[2].Trim().Trim('"').Trim("'")
        if ($name -eq 'FSD_AMAP_WEB_SERVICE_KEY' -and $val) {
            $env:FSD_AMAP_WEB_SERVICE_KEY = $val
            Write-Host "Loaded FSD_AMAP_WEB_SERVICE_KEY from .env" -ForegroundColor DarkGray
        }
    }
}
if ($env:FSD_AMAP_WEB_SERVICE_KEY) {
    Write-Host "Amap driving: enabled (Web Service Key set)" -ForegroundColor DarkGray
} else {
    Write-Host "Amap driving: off (set FSD_AMAP_WEB_SERVICE_KEY in .env or env)" -ForegroundColor Yellow
}

Write-Host "Starting backend on http://localhost:8080 ..." -ForegroundColor Cyan
Set-Location fsd-bootstrap
mvn spring-boot:run
