# Build GraphHopper JAR with database connection fixes (PowerShell version)
# This script builds the GraphHopper web-bundle JAR that includes GTFS support

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Building GraphHopper with Database Fixes" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Navigate to the graphhopper directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host ""
Write-Host "1. Cleaning previous builds..." -ForegroundColor Yellow
mvn clean

Write-Host ""
Write-Host "2. Building GraphHopper (skipping tests)..." -ForegroundColor Yellow
mvn package -DskipTests

Write-Host ""
Write-Host "3. Locating the built JAR..." -ForegroundColor Yellow
$JarFile = Get-ChildItem -Path . -Filter "graphhopper-web-*.jar" -Recurse -File | 
    Where-Object { $_.Name -notmatch "sources" -and $_.Name -notmatch "javadoc" } | 
    Select-Object -First 1

if (-not $JarFile) {
    Write-Host "ERROR: Could not find built JAR file!" -ForegroundColor Red
    Write-Host "Expected to find graphhopper-web-*.jar in web-bundle/target/" -ForegroundColor Red
    exit 1
}

Write-Host "Found JAR: $($JarFile.FullName)" -ForegroundColor Green

Write-Host ""
Write-Host "4. Copying JAR to journey_planning_interface_dev..." -ForegroundColor Yellow
$DestPath = Join-Path $ScriptDir "..\journey_planning_interface_dev\graphhopper.jar"
Copy-Item -Path $JarFile.FullName -Destination $DestPath -Force

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Build Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "The new graphhopper.jar has been copied to journey_planning_interface_dev/" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Rebuild the Docker image for journey_planning_interface_dev" -ForegroundColor White
Write-Host "2. Redeploy to DigitalOcean" -ForegroundColor White
Write-Host ""


