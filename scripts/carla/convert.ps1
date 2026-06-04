# OSM -> OpenDRIVE -> CARLA package (DispatchFlow)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "== ZJF pilot: OSM -> OpenDRIVE ==" -ForegroundColor Cyan
python "$Root\scripts\carla\osm_to_opendrive.py" @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Outputs:" -ForegroundColor Green
Write-Host "  data/carla/zjf_pilot.xodr"
Write-Host "  data/carla/manifest.json"
Write-Host "  data/carla/import_package/"
Write-Host ""
Write-Host "Next (CARLA standalone, no FBX):" -ForegroundColor Yellow
Write-Host "  1. Start CARLA simulator"
Write-Host "  2. python scripts/carla/load_in_carla.py"
Write-Host ""
Write-Host "Next (CARLA source build + Unreal import):" -ForegroundColor Yellow
Write-Host "  1. Copy data/carla/import_package/ZjfDieshiqiaoPilot -> <CARLA>/Import/"
Write-Host "  2. cd <CARLA> ; make import ARGS=`"--package=ZjfDieshiqiaoPilot`""
