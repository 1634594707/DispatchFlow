# 编译全部模块并启动 fsd-bootstrap（避免园区等新接口 404）
Set-Location $PSScriptRoot
Write-Host "Building modules (clean install)..." -ForegroundColor Cyan
mvn -pl fsd-bootstrap -am clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "Applying charset fix (V5) if MySQL container is running..." -ForegroundColor Cyan
docker cp "$PSScriptRoot\sql\migrations\V05__fix_charset_data.sql" fsd-mysql:/tmp/V05.sql 2>$null
if ($LASTEXITCODE -eq 0) {
    docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V05.sql" 2>$null
}

Write-Host "Starting backend on http://localhost:8080 ..." -ForegroundColor Cyan
Set-Location fsd-bootstrap
mvn spring-boot:run
