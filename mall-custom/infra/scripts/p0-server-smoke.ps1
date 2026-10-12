param(
  [string]$AdminUsername = 'admin',
  [string]$AdminBase = 'http://127.0.0.1:18880',
  [string]$PortalBase = 'http://127.0.0.1:18885',
  [string]$PublicBase = 'http://122.51.105.107'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$passDir = Join-Path $projectRoot '.tools'
$passed = 0
$failed = 0
function Check([string]$Name, [scriptblock]$Action) {
  try {
    if (& $Action) { "PASS $Name"; $script:passed++ }
    else { "FAIL $Name"; $script:failed++ }
  } catch {
    "FAIL $Name : $($_.Exception.Message)"
    $script:failed++
  }
}
$adminPassword = (Get-Content (Join-Path $passDir 'deploy-demo-admin-password.txt') -Raw).Trim()
$memberPassword = (Get-Content (Join-Path $passDir 'deploy-demo-member-password.txt') -Raw).Trim()
$adminLogin = Invoke-RestMethod -Method Post -Uri "$AdminBase/admin/login" -ContentType 'application/json' -Body (@{username=$AdminUsername;password=$adminPassword}|ConvertTo-Json)
Check 'admin-login' { $adminLogin.code -eq 200 -and [bool]$adminLogin.data.token }
$adminHeaders = @{Authorization=($adminLogin.data.tokenHead+$adminLogin.data.token)}
foreach ($module in @('members','partner','cofounder','points','finance','dividend','settings')) {
  Check "admin-$module" { (Invoke-RestMethod -Uri "$AdminBase/p0/$module" -Headers $adminHeaders).code -eq 200 }
}
$memberLogin = Invoke-RestMethod -Method Post -Uri "$PortalBase/sso/login" -Body @{username='demo_member';password=$memberPassword}
Check 'member-login' { $memberLogin.code -eq 200 -and [bool]$memberLogin.data.token }
$memberHeaders = @{Authorization=($memberLogin.data.tokenHead+$memberLogin.data.token)}
Check 'member-identity' { (Invoke-RestMethod -Uri "$PortalBase/member/identity/current" -Headers $memberHeaders).code -eq 200 }
Check 'member-points' { (Invoke-RestMethod -Uri "$PortalBase/member/points/account?pointsType=1" -Headers $memberHeaders).code -eq 200 }
Check 'partner-config' { (Invoke-RestMethod -Uri "$PortalBase/member/partner/config" -Headers $memberHeaders).code -eq 200 }
$day = ([DateTimeOffset]::new(2026,10,10,0,0,0,[TimeSpan]::FromHours(8))).ToUnixTimeMilliseconds()
Check 'member-own-snapshot' { (Invoke-RestMethod -Uri "$PortalBase/member/dividend/mySnapshot?statDate=$day" -Headers $memberHeaders).code -eq 200 }
Check 'consumer-points-write-blocked' {
  try { Invoke-WebRequest -Method Post -Uri "$PortalBase/member/points/change" -Headers $memberHeaders -ContentType 'application/json' -Body '{"pointsType":1,"changeType":"adjust","amount":1,"idempotencyKey":"blocked-smoke-20261010"}' | Out-Null; $false }
  catch { [int]$_.Exception.Response.StatusCode -eq 403 }
}
Check 'consumer-contribution-write-blocked' {
  try { Invoke-WebRequest -Method Post -Uri "$PortalBase/member/dividend/contribution" -Headers $memberHeaders -ContentType 'application/json' -Body '{"eventType":"order_complete","bizId":"blocked-smoke","contributionValue":1,"statDate":0}' | Out-Null; $false }
  catch { [int]$_.Exception.Response.StatusCode -eq 403 }
}
Check 'public-storefront' { (Invoke-WebRequest -Uri "$PublicBase/" -UseBasicParsing).StatusCode -eq 200 }
Check 'public-admin' { (Invoke-WebRequest -Uri "$PublicBase/admin/" -UseBasicParsing).StatusCode -eq 200 }
"RESULT PASS=$passed FAIL=$failed"
if ($failed -gt 0) { exit 1 }
