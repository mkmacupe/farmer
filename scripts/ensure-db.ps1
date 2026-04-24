Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvFilePath = Join-Path $ProjectRoot ".env"
$ExpectedPort = 5433
$ExpectedUser = "sales"
$ExpectedDatabase = "farm_sales"

function Test-DockerReady {
  try {
    $serverVersion = docker version --format "{{.Server.Version}}" 2>$null
    return $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($serverVersion | Out-String))
  } catch {
    return $false
  }
}

function Start-DockerDesktopIfNeeded {
  if (Test-DockerReady) {
    return
  }

  $candidatePaths = @(
    (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"),
    (Join-Path $env:ProgramW6432 "Docker\Docker\Docker Desktop.exe"),
    (Join-Path $env:LocalAppData "Docker\Docker\Docker Desktop.exe")
  ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

  $dockerDesktopPath = $candidatePaths | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
  if (-not $dockerDesktopPath) {
    throw "Docker Desktop executable was not found."
  }

  if (-not (Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue)) {
    Start-Process -FilePath $dockerDesktopPath -WindowStyle Hidden | Out-Null
  }

  $deadline = (Get-Date).AddMinutes(4)
  while ((Get-Date) -lt $deadline) {
    if (Test-DockerReady) {
      return
    }
    Start-Sleep -Seconds 5
  }

  throw "Docker daemon did not become ready in 4 minutes."
}

function Read-EnvFile {
  param([string]$Path)

  $values = @{}
  if (-not (Test-Path -LiteralPath $Path)) {
    return $values
  }

  foreach ($line in Get-Content -LiteralPath $Path) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith("#")) {
      continue
    }

    $parts = $trimmed -split "=", 2
    if ($parts.Count -eq 2 -and -not [string]::IsNullOrWhiteSpace($parts[0])) {
      $values[$parts[0].Trim()] = $parts[1].Trim()
    }
  }

  return $values
}

function Ensure-EnvPort {
  if (-not (Test-Path -LiteralPath $EnvFilePath)) {
    throw ".env was not found: $EnvFilePath"
  }

  $lines = [System.Collections.Generic.List[string]]::new()
  $hasPort = $false
  foreach ($line in Get-Content -LiteralPath $EnvFilePath) {
    if ($line -match "^\s*POSTGRES_PORT\s*=") {
      $lines.Add("POSTGRES_PORT=$ExpectedPort")
      $hasPort = $true
    } else {
      $lines.Add($line)
    }
  }

  if (-not $hasPort) {
    $lines.Add("POSTGRES_PORT=$ExpectedPort")
  }

  Set-Content -LiteralPath $EnvFilePath -Value $lines -Encoding ascii
}

function Test-PostgresConnection {
  $envValues = Read-EnvFile -Path $EnvFilePath
  if (-not $envValues.ContainsKey("POSTGRES_PASSWORD")) {
    return $false
  }

  $psql = @(
    "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe"
  ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

  if (-not $psql) {
    return $true
  }

  $previousPassword = $env:PGPASSWORD
  try {
    $env:PGPASSWORD = $envValues["POSTGRES_PASSWORD"]
    & $psql -h 127.0.0.1 -p $ExpectedPort -U $ExpectedUser -d $ExpectedDatabase -c "select 1;" *> $null
    return $LASTEXITCODE -eq 0
  } finally {
    if ($null -eq $previousPassword) {
      Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } else {
      $env:PGPASSWORD = $previousPassword
    }
  }
}

Set-Location -LiteralPath $ProjectRoot
Ensure-EnvPort
Start-DockerDesktopIfNeeded
docker compose --env-file "$EnvFilePath" up -d db | Out-Host

$deadline = (Get-Date).AddMinutes(2)
while ((Get-Date) -lt $deadline) {
  if (Test-PostgresConnection) {
    Write-Host "Farm Sales PostgreSQL is ready on localhost:$ExpectedPort."
    exit 0
  }
  Start-Sleep -Seconds 3
}

throw "Farm Sales PostgreSQL did not become reachable on localhost:$ExpectedPort."
