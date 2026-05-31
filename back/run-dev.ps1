# 编译全部模块并启动 fsd-bootstrap（避免园区等新接口 404）
Set-Location $PSScriptRoot
Write-Host "Building modules (clean install)..." -ForegroundColor Cyan
mvn -pl fsd-bootstrap -am clean install -DskipTests
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

Write-Host "Starting backend on http://localhost:8080 ..." -ForegroundColor Cyan
Set-Location fsd-bootstrap
mvn spring-boot:run
