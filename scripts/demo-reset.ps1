param(
  [string[]]$Base
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ManagerUsername = "manager"
$ManagerPassword = "MgrD5v8cN4"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-LocalRest {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Uri,
    [hashtable]$Headers,
    [string]$ContentType,
    [string]$Body
  )

  $args = @{
    Method = $Method
    Uri = $Uri
  }
  if ($PSBoundParameters.ContainsKey("Headers")) {
    $args.Headers = $Headers
  }
  if ($PSBoundParameters.ContainsKey("ContentType")) {
    $args.ContentType = $ContentType
  }
  if ($PSBoundParameters.ContainsKey("Body")) {
    $args.Body = $Body
  }
  if ((Get-Command Invoke-RestMethod).Parameters.ContainsKey("NoProxy")) {
    $args.NoProxy = $true
  }
  return Invoke-RestMethod @args
}

function Test-BackendAvailability {
  param(
    [Parameter(Mandatory = $true)][string]$Base
  )

  $healthUri = (($Base -replace "/api/?$", "") + "/actuator/health")
  try {
    $health = Invoke-LocalRest -Method Get -Uri $healthUri
    return $health.status -eq "UP"
  } catch {
    return $false
  }
}

function Resolve-FrontendConfiguredBase {
  $envFiles = @(
    (Join-Path $ProjectRoot "frontend/.env.local"),
    (Join-Path $ProjectRoot "frontend/.env")
  )

  foreach ($envFile in $envFiles) {
    if (-not (Test-Path $envFile)) {
      continue
    }

    $configuredBase = $null
    $configuredHost = "127.0.0.1"
    $configuredPort = $null

    foreach ($line in Get-Content -Path $envFile) {
      $trimmed = $line.Trim()
      if ($trimmed -match '^VITE_API_BASE\s*=\s*(.+)$') {
        $configuredBase = $Matches[1].Trim()
        continue
      }
      if ($trimmed -match '^VITE_PROXY_API_HOST\s*=\s*(.+)$') {
        $configuredHost = $Matches[1].Trim()
        continue
      }
      if ($trimmed -match '^VITE_PROXY_API_PORT\s*=\s*(\d+)$') {
        $configuredPort = $Matches[1]
      }
    }

    if ($configuredBase -and $configuredBase -match '^https?://') {
      return $configuredBase
    }
    if ($configuredPort) {
      return "http://$configuredHost`:$configuredPort/api"
    }
  }

  return $null
}

function Resolve-ResetBases {
  if ($Base -and $Base.Count -gt 0) {
    return $Base
  }

  $frontendConfiguredBase = Resolve-FrontendConfiguredBase
  if ($frontendConfiguredBase -and (Test-BackendAvailability -Base $frontendConfiguredBase)) {
    return @($frontendConfiguredBase)
  }

  $candidates = @(
    "http://127.0.0.1:8080/api",
    "http://127.0.0.1:8081/api"
  )

  $available = @($candidates | Where-Object { Test-BackendAvailability -Base $_ } | Select-Object -Unique)
  if ($available.Count -gt 0) {
    return $available
  }

  Write-Host "Local backend was not auto-detected. Falling back to http://127.0.0.1:8080/api."
  return @("http://127.0.0.1:8080/api")
}

function Invoke-DemoReset {
  param(
    [Parameter(Mandatory = $true)][string]$Base
  )

  $loginBody = @{
    username = $ManagerUsername
    password = $ManagerPassword
  } | ConvertTo-Json

  $auth = Invoke-LocalRest `
    -Method Post `
    -Uri "$Base/auth/login" `
    -ContentType "application/json" `
    -Body $loginBody

  $headers = @{
    Authorization = "Bearer $($auth.token)"
  }

  return Invoke-LocalRest `
    -Method Post `
    -Uri "$Base/demo/reset" `
    -Headers $headers
}

$basesToReset = Resolve-ResetBases
$results = New-Object System.Collections.Generic.List[object]

foreach ($resolvedBase in $basesToReset) {
  Write-Host "Reset base: $resolvedBase"
  $reset = Invoke-DemoReset -Base $resolvedBase
  Write-Host "Demo scenario reset completed."
  Write-Host "Recommended flow:"
  foreach ($step in $reset.defenseFlow) {
    Write-Host " - $step"
  }

  $results.Add([pscustomobject]@{
      base = $resolvedBase
      reset = $reset
    })
}

if ($results.Count -eq 1) {
  $results[0].reset | ConvertTo-Json -Depth 5
  return
}

[pscustomobject]@{
  resetBases = $results.base
  results = $results
} | ConvertTo-Json -Depth 6
