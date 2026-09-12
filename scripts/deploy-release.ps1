[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('hml','prod')][string]$Environment)
$ErrorActionPreference = 'Stop'
$release = "oficina-$Environment"
foreach ($name in @('IMAGE_DIGEST','ECR_REPOSITORY_URL','DB_ENDPOINT','DB_SECRET_ARN','JWT_SECRET_ARN','STAFF_SECRET_ARN','API_BASE_URL')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Required deployment variable missing: $name" }
}
if ($env:IMAGE_DIGEST -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Invalid immutable image digest' }
$demoFixtures = if ([string]::IsNullOrEmpty($env:ENABLE_DEMO_FIXTURES)) { 'false' } else { $env:ENABLE_DEMO_FIXTURES }
if ($demoFixtures -cnotin @('true','false')) { throw 'ENABLE_DEMO_FIXTURES must be true or false' }
$existingJson = helm list --namespace $Environment --deployed --failed --pending --superseded --uninstalled --uninstalling --filter "^$release`$" --output json
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing Helm release; refusing deployment' }
$existed = @($existingJson | ConvertFrom-Json).Count -gt 0
$previous = $null
if ($existed) {
    $historyJson = helm history $release --namespace $Environment --output json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect rollback history; refusing deployment' }
    $previous = @($historyJson | ConvertFrom-Json | Where-Object status -eq deployed | Sort-Object revision -Descending)[0]
    if (-not $previous) { throw 'Existing release has no deployed revision; remove the failed release before retrying' }
}
function Remove-FailedFirstInstall {
    helm uninstall $release --namespace $Environment --ignore-not-found --wait --timeout 10m
    if ($LASTEXITCODE -ne 0) { throw 'Failed first-install removal failed' }
    $selector = "app.kubernetes.io/instance=$release,oficina.environment=$Environment,oficina.hook-resource=true"
    kubectl -n $Environment delete configmap,secretproviderclass,jobs -l $selector --wait --timeout=120s
    if ($LASTEXITCODE -ne 0) { throw 'Failed first-install hook cleanup failed' }
}
$arguments = @('upgrade','--install',$release,'deploy/helm/oficina','--namespace',$Environment,
    '-f',"deploy/helm/oficina/values-$Environment.yaml",'--atomic','--wait','--timeout','10m','--history-max','10',
    '--set-string',"image.repository=$env:ECR_REPOSITORY_URL",'--set-string',"image.digest=$env:IMAGE_DIGEST",
    '--set-string',"db.endpoint=$env:DB_ENDPOINT",'--set-string',"db.secretArn=$env:DB_SECRET_ARN",
    '--set-string',"jwt.secretArn=$env:JWT_SECRET_ARN",'--set-string',"staff.secretArn=$env:STAFF_SECRET_ARN",
    '--set-string',"gitSha=$env:RELEASE_SHA",'--set',"demoFixtures.enabled=$demoFixtures")
helm @arguments
if ($LASTEXITCODE -ne 0) {
    if (-not $existed) { Remove-FailedFirstInstall }
    throw 'Helm deployment failed; atomic recovery was requested'
}
try {
    & (Join-Path $PSScriptRoot 'smoke-test.ps1') -ApiUrl $env:API_BASE_URL
} catch {
    if ($previous) {
        helm rollback $release $previous.revision --namespace $Environment --wait --timeout 10m
        if ($LASTEXITCODE -ne 0) { throw 'External smoke failed and rollback failed; manual recovery required' }
    } elseif (-not $existed) {
        Remove-FailedFirstInstall
    } else {
        throw 'External smoke failed; retained release has no previously healthy revision for rollback'
    }
    throw 'External smoke failed; previous application release restored or failed first install removed'
}
python (Join-Path $PSScriptRoot 'cleanup-hooks.py') $Environment $release
if ($LASTEXITCODE -ne 0) { throw 'Release succeeded but hook resource cleanup failed' }
