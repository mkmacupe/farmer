@echo off
setlocal
rem Root launcher for Windows users. The implementation lives in start-dev.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-dev.ps1" %*
if errorlevel 1 (
  echo.
  echo Script failed. Press any key to close.
  pause >nul
)
endlocal
