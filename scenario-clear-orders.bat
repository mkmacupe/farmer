@echo off
setlocal
chcp 65001 >nul
rem Root launcher for Windows users. The implementation lives in scripts\scenario-clear-orders.ps1.

set "PS_EXE=powershell"
where /q pwsh.exe
if not errorlevel 1 set "PS_EXE=pwsh"

if "%~1"=="" (
  %PS_EXE% -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\scenario-clear-orders.ps1"
) else (
  %PS_EXE% -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\scenario-clear-orders.ps1" -Base "%~1"
)
if errorlevel 1 (
  echo.
  echo Scenario clear-orders failed.
  echo Try passing the backend base manually, for example:
  echo   scenario-clear-orders.bat http://127.0.0.1:8080/api
  echo   scenario-clear-orders.bat https://farm-sales-backend.onrender.com/api
  pause >nul
)

endlocal
