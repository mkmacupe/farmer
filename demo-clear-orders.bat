@echo off
setlocal
chcp 65001 >nul

set "PS_EXE=powershell"
where /q pwsh.exe
if not errorlevel 1 set "PS_EXE=pwsh"

if "%~1"=="" (
  %PS_EXE% -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo-clear-orders.ps1"
) else (
  %PS_EXE% -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo-clear-orders.ps1" -Base "%~1"
)
if errorlevel 1 (
  echo.
  echo Demo clear-orders failed.
  echo Try passing the backend base manually, for example:
  echo   demo-clear-orders.bat http://127.0.0.1:8080/api
  echo   demo-clear-orders.bat https://farm-sales-backend.onrender.com/api
  pause >nul
)

endlocal
