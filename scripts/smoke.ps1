param(
  [string]$Base = "http://127.0.0.1:8080/api"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

 $DemoUserPasswords = @{
  "mogilevkhim" = "MhvK8r2pQ1"
  "mogilevlift" = "MlvT4n7xR2"
  "babushkina" = "BbkP6m9sL3"
  "manager" = "MgrD5v8cN4"
  "logistician" = "LogS7q1wE5"
  "driver1" = "Drv1A9k2Z6"
  "driver2" = "Drv2B8m3Y7"
  "driver3" = "Drv3C7n4X8"
}

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

function Invoke-LocalWeb {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Uri,
    [hashtable]$Headers,
    [Parameter(Mandatory = $true)][string]$OutFile
  )

  $args = @{
    Method = $Method
    Uri = $Uri
    OutFile = $OutFile
  }
  if ($PSBoundParameters.ContainsKey("Headers")) {
    $args.Headers = $Headers
  }
  if ((Get-Command Invoke-WebRequest).Parameters.ContainsKey("NoProxy")) {
    $args.NoProxy = $true
  }
  Invoke-WebRequest @args | Out-Null
}

function Login {
  param([string]$Username)

  if (-not $DemoUserPasswords.ContainsKey($Username)) {
    throw "Unknown demo username '$Username'."
  }

  $body = @{
    username = $Username
    password = $DemoUserPasswords[$Username]
  } | ConvertTo-Json

  return Invoke-LocalRest `
    -Method Post `
    -Uri "$Base/auth/login" `
    -ContentType "application/json" `
    -Body $body
}

function AuthHeaders {
  param([string]$Token)
  return @{ Authorization = "Bearer $Token" }
}

function Normalize-Collection {
  param([Parameter(Mandatory = $true)]$Payload)

  if ($null -eq $Payload) {
    return @()
  }
  if ($Payload -is [array]) {
    return $Payload
  }
  if ($Payload.PSObject.Properties["items"] -and $Payload.items -is [array]) {
    return $Payload.items
  }
  if ($Payload.PSObject.Properties["items"] -and $Payload.items) {
    return @($Payload.items)
  }
  return @($Payload)
}

$director = Login -Username "mogilevkhim"
$manager = Login -Username "manager"
$logistician = Login -Username "logistician"
$driver = Login -Username "driver1"

$productsPayload = Invoke-LocalRest -Method Get -Uri "$Base/products" -Headers (AuthHeaders $director.token)
$products = Normalize-Collection -Payload $productsPayload
if (-not $products -or $products.Count -lt 1) {
  throw "No products returned from API."
}

$availableProduct = $products | Where-Object { $_.stockQuantity -gt 0 } | Select-Object -First 1
if (-not $availableProduct) {
  throw "No products with positive stock are available."
}

$addresses = Invoke-LocalRest -Method Get -Uri "$Base/director/addresses" -Headers (AuthHeaders $director.token)
if (-not $addresses -or $addresses.Count -lt 1) {
  throw "No delivery addresses returned for director."
}

$createBody = @{
  deliveryAddressId = $addresses[0].id
  items = @(
    @{
      productId = $availableProduct.id
      quantity = 1
    }
  )
} | ConvertTo-Json -Depth 5

$created = Invoke-LocalRest `
  -Method Post `
  -Uri "$Base/orders" `
  -Headers (AuthHeaders $director.token) `
  -ContentType "application/json" `
  -Body $createBody

if ($created.status -ne "CREATED") {
  throw "Expected CREATED status, got $($created.status)"
}

$orderId = $created.id
$approved = Invoke-LocalRest `
  -Method Post `
  -Uri "$Base/orders/$orderId/approve" `
  -Headers (AuthHeaders $manager.token)

if ($approved.status -ne "APPROVED") {
  throw "Expected APPROVED status, got $($approved.status)"
}

$drivers = Invoke-LocalRest -Method Get -Uri "$Base/users/drivers" -Headers (AuthHeaders $logistician.token)
$demoDriver = $drivers | Where-Object { $_.username -eq "driver1" } | Select-Object -First 1
if (-not $demoDriver) {
  throw "Demo driver 'driver1' was not found in /users/drivers."
}

$assignBody = @{
  driverId = $demoDriver.id
} | ConvertTo-Json

$assigned = Invoke-LocalRest `
  -Method Post `
  -Uri "$Base/orders/$orderId/assign-driver" `
  -Headers (AuthHeaders $logistician.token) `
  -ContentType "application/json" `
  -Body $assignBody

if ($assigned.status -ne "ASSIGNED") {
  throw "Expected ASSIGNED status, got $($assigned.status)"
}

$driverOrders = Invoke-LocalRest -Method Get -Uri "$Base/orders/assigned" -Headers (AuthHeaders $driver.token)
if (-not ($driverOrders | Where-Object { $_.id -eq $orderId })) {
  throw "Assigned order $orderId is missing in /orders/assigned for demo driver."
}

$delivered = Invoke-LocalRest `
  -Method Post `
  -Uri "$Base/orders/$orderId/deliver" `
  -Headers (AuthHeaders $driver.token)

if ($delivered.status -ne "DELIVERED") {
  throw "Expected DELIVERED status, got $($delivered.status)"
}

$timeline = Invoke-LocalRest -Method Get -Uri "$Base/orders/$orderId/timeline" -Headers (AuthHeaders $director.token)
if (-not $timeline -or $timeline.Count -lt 1) {
  throw "Timeline for order $orderId is empty."
}

$from = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd")
$to = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$reportPath = Join-Path $env:TEMP ("orders-report-smoke-" + [guid]::NewGuid().ToString() + ".xlsx")

Invoke-LocalWeb `
  -Method Get `
  -Uri "$Base/reports/orders?from=$from&to=$to&status=DELIVERED" `
  -Headers (AuthHeaders $manager.token) `
  -OutFile $reportPath

$reportBytes = (Get-Item $reportPath).Length
if ($reportBytes -le 0) {
  throw "Report file is empty."
}
Remove-Item $reportPath -Force

[PSCustomObject]@{
  base = $Base
  orderId = $orderId
  statuses = @($created.status, $approved.status, $assigned.status, $delivered.status) -join " -> "
  timelineEvents = $timeline.Count
  reportBytes = $reportBytes
} | ConvertTo-Json -Compress
