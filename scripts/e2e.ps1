$ErrorActionPreference = 'Stop'

$BaseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { 'http://localhost:8080' }
$MailhogUrl = if ($env:MAILHOG_URL) { $env:MAILHOG_URL } else { 'http://localhost:8025' }
$Suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$TenantName = "Stage 1 E2E $Suffix"
$AdminEmail = "admin-$Suffix@e2e.test"
$Password = "E2ePassword-$Suffix"
$Period = (Get-Date).ToUniversalTime().ToString('yyyy-MM')

$healthUrls = @("$BaseUrl/actuator/health")
if ($BaseUrl -eq 'http://localhost:8080' -or $BaseUrl -eq 'http://127.0.0.1:8080') {
    $healthUrls += @(
        'http://localhost:8081/actuator/health',
        'http://localhost:8082/actuator/health',
        'http://localhost:8083/actuator/health',
        'http://localhost:8084/actuator/health',
        'http://localhost:8085/actuator/health'
    )
}
$servicesReady = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $servicesReady = $true
    foreach ($healthUrl in $healthUrls) {
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
            if ($health.status -ne 'UP') { $servicesReady = $false }
        } catch {
            $servicesReady = $false
        }
    }
    if ($servicesReady) { break }
    Start-Sleep -Seconds 1
}
if (-not $servicesReady) { throw 'Required application services did not become healthy within 60 seconds' }

function Invoke-JsonApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        ContentType = 'application/json'
    }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 8 }
    Invoke-RestMethod @parameters
}

Write-Output "Registering tenant $TenantName"
$registration = Invoke-JsonApi POST '/auth/register-tenant' @{
    tenantName = $TenantName
    adminEmail = $AdminEmail
    password = $Password
    planType = 'FLAT_RATE'
} ''
$login = Invoke-JsonApi POST '/auth/login' @{ email = $AdminEmail; password = $Password } ''
$token = $login.accessToken

$anonymousStatus = 200
try {
    Invoke-RestMethod -Uri "$BaseUrl/devices" | Out-Null
} catch {
    $anonymousStatus = $_.Exception.Response.StatusCode.value__
}
if ($anonymousStatus -ne 401) { throw "Expected anonymous device request to return 401, got $anonymousStatus" }

$readerEmail = "reader-$Suffix@e2e.test"
Invoke-JsonApi POST '/auth/users' @{ email = $readerEmail; password = $Password; role = 'ROLE_USER' } $token | Out-Null
$readerLogin = Invoke-JsonApi POST '/auth/login' @{ email = $readerEmail; password = $Password } ''
$forbiddenStatus = 200
try {
    Invoke-JsonApi POST "/billing/invoices/generate?period=$Period" $null $readerLogin.accessToken | Out-Null
} catch {
    $forbiddenStatus = $_.Exception.Response.StatusCode.value__
}
if ($forbiddenStatus -ne 403) { throw "Expected regular user invoice generation to return 403, got $forbiddenStatus" }

$device = Invoke-JsonApi POST '/devices' @{ name = "E2E Device $Suffix"; type = 'BROWSER'; os = 'Windows' } $token
$session = Invoke-JsonApi POST "/devices/$($device.id)/sessions/start" $null $token
Invoke-JsonApi POST "/sessions/$($session.sessionId)/end" $null $token | Out-Null

$summary = $null
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $summary = Invoke-JsonApi GET "/usage/summary?period=$Period" $null $token
    if ($summary.totalSessions -ge 1) { break }
    Start-Sleep -Seconds 1
}
if ($summary.totalSessions -lt 1) { throw 'Usage event was not aggregated within 30 seconds' }

$invoice = Invoke-JsonApi POST "/billing/invoices/generate?period=$Period" $null $token
$repeatInvoice = Invoke-JsonApi POST "/billing/invoices/generate?period=$Period" $null $token
if ($repeatInvoice.id -ne $invoice.id) { throw 'Invoice generation was not idempotent' }

$mail = $null
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $mail = Invoke-RestMethod -Uri "$MailhogUrl/api/v2/search?kind=to&query=$([uri]::EscapeDataString($AdminEmail))"
    if ($mail.total -ge 1) { break }
    Start-Sleep -Seconds 1
}
if ($mail.total -lt 1) { throw 'Invoice email was not visible in MailHog within 30 seconds' }

Write-Output "E2E passed: tenant=$($registration.tenantId); sessions=$($summary.totalSessions); invoice=$($invoice.id); total=$($invoice.totalAmount) $($invoice.currency); email=$AdminEmail"