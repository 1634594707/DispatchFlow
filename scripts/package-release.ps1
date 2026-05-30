# Build and zip DispatchFlow release artifacts.
# Usage: .\scripts\package-release.ps1 -Version v2.0.0

param(
    [Parameter(Mandatory = $true)]
    [string]$Version
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$OutDir = Join-Path $Root "dist\release\$Version"
$ZipDir = Join-Path $Root "dist\release"

if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "backend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "frontend") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "sql\migrations") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "deploy") | Out-Null

Write-Host ">> Maven package (backend) ..."
Push-Location (Join-Path $Root "back")
mvn -q -pl fsd-bootstrap -am clean package -DskipTests
Pop-Location

$JarSrc = Join-Path $Root "back\fsd-bootstrap\target\fsd-bootstrap-0.1.0-SNAPSHOT.jar"
$JarDst = Join-Path $OutDir "backend\fsd-core-server.jar"
Copy-Item $JarSrc $JarDst

Write-Host ">> npm build (frontend) ..."
Push-Location (Join-Path $Root "front")
if (-not (Test-Path "node_modules")) { npm ci }
npm run build
Pop-Location

Copy-Item -Recurse (Join-Path $Root "front\dist\*") (Join-Path $OutDir "frontend\")
Copy-Item -Recurse (Join-Path $Root "back\sql\migrations\*") (Join-Path $OutDir "sql\migrations\")
Copy-Item (Join-Path $Root "back\docker-compose.yml") (Join-Path $OutDir "deploy\docker-compose.yml")
Copy-Item (Join-Path $Root "back\Dockerfile") (Join-Path $OutDir "deploy\")
Copy-Item (Join-Path $Root ".env.example") (Join-Path $OutDir "deploy\")
Copy-Item (Join-Path $Root "docs\DEPLOYMENT.md") (Join-Path $OutDir "DEPLOYMENT.md")
Copy-Item (Join-Path $Root "docs\releases\$Version.md") (Join-Path $OutDir "RELEASE_NOTES.md") -ErrorAction SilentlyContinue

@(
"# DispatchFlow $Version",
"",
"## Contents",
"- backend/fsd-core-server.jar — Spring Boot API (Java 21)",
"- frontend/ — static files (serve via Nginx or CDN)",
"- sql/migrations/ — MySQL scripts V01..V14",
"- deploy/ — docker-compose.yml + Dockerfile (run from deploy/; mount sql from bundle)",
"",
"## Quick start (Docker)",
"  cd deploy",
"  docker compose up -d --build",
"  # Apply missing SQL manually if upgrading an existing DB (see DEPLOYMENT.md)",
"",
"## Run JAR without Docker image rebuild",
"  java -jar backend/fsd-core-server.jar",
"  # Requires MySQL, Redis, RabbitMQ per DEPLOYMENT.md",
""
) | Set-Content -Encoding UTF8 (Join-Path $OutDir "PACKAGE_README.txt")

$Tag = $Version.TrimStart("v")
$BundleZip = Join-Path $ZipDir "DispatchFlow-$Version-bundle.zip"
$BackendZip = Join-Path $ZipDir "DispatchFlow-$Version-backend.jar.zip"
$FrontendZip = Join-Path $ZipDir "DispatchFlow-$Version-frontend-dist.zip"

if (Test-Path $BundleZip) { Remove-Item -Force $BundleZip }
if (Test-Path $BackendZip) { Remove-Item -Force $BackendZip }
if (Test-Path $FrontendZip) { Remove-Item -Force $FrontendZip }

Compress-Archive -Path $OutDir -DestinationPath $BundleZip
Compress-Archive -Path $JarDst -DestinationPath $BackendZip
Compress-Archive -Path (Join-Path $OutDir "frontend\*") -DestinationPath $FrontendZip

Write-Host ""
Write-Host "Created:"
Write-Host "  $BundleZip"
Write-Host "  $BackendZip"
Write-Host "  $FrontendZip"
