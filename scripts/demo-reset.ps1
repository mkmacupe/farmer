param(
  [string]$Base = "http://127.0.0.1:8080/api"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ManagerUsername = "manager"
$ManagerPassword = "MgrD5v8cN4"

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

$reset = Invoke-LocalRest `
  -Method Post `
  -Uri "$Base/demo/reset" `
  -Headers $headers

Write-Host "Demo scenario reset completed."
Write-Host "Recommended flow:"
foreach ($step in $reset.defenseFlow) {
  Write-Host " - $step"
}

$reset | ConvertTo-Json -Depth 5
