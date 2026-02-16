@echo off
setlocal
set "PWCLI=C:\\Users\\allam\\.codex\\skills\\playwright\\scripts\\playwright_cli.sh"
if not exist "%PWCLI%" (
  echo Playwright CLI not found at %PWCLI%
  exit /b 1
)
bash -lc "/mnt/c/Users/allam/.codex/skills/playwright/scripts/playwright_cli.sh %*"
endlocal
