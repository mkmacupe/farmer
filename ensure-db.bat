@echo off
setlocal
chcp 65001 >nul
rem Starts Docker Desktop if needed and keeps the Farm Sales PostgreSQL container available on localhost:5433.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\ensure-db.ps1" %*
if errorlevel 1 (
  echo.
  echo Farm Sales database startup failed. Keep this window open and check the message above.
  pause
)
