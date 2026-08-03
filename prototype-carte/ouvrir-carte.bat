@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "HERE=%~dp0"
set "PORT=8754"

where powershell.exe >nul 2>nul
if %errorlevel%==0 (
  start "GeoTower carte locale" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%HERE%serve-carte.ps1" -Port %PORT%
  timeout /t 1 /nobreak >nul
  start "" "http://127.0.0.1:%PORT%/index.html"
  exit /b
)

start "" "%HERE%index.html"
