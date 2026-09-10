# Behavioral recovery checks with command doubles: no AWS, Kubernetes or network.
$ErrorActionPreference = 'Stop'
$global:deliveryTestScenario = ''
$global:deliveryTestCalls = [System.Collections.Generic.List[string]]::new()
function helm {
    $global:deliveryTestCalls.Add('helm ' + ($args -join ' '))
    $global:LASTEXITCODE = 0
    switch ($args[0]) {
        'list' {
            if ($global:deliveryTestScenario -eq 'inspection-fails') { $global:LASTEXITCODE = 1; return }
            if ($global:deliveryTestScenario -in @('smoke-fails-existing','existing-unhealthy')) { '[{"name":"oficina-hml"}]' } else { '[]' }
        }
        'history' {
            if ($global:deliveryTestScenario -eq 'existing-unhealthy') { '[{"revision":3,"status":"failed"}]' }
            else { '[{"revision":3,"status":"deployed"}]' }
        }
        'upgrade' { if ($global:deliveryTestScenario -eq 'helm-fails-first') { $global:LASTEXITCODE = 1 } }
    }
}
function kubectl { $global:deliveryTestCalls.Add('kubectl ' + ($args -join ' ')); $global:LASTEXITCODE = 0 }
function python { $global:deliveryTestCalls.Add('python ' + ($args -join ' ')); $global:LASTEXITCODE = 0 }
function Invoke-RestMethod {
    if ($global:deliveryTestScenario -like 'smoke-fails-*') { throw 'Synthetic health failure' }
    @{ status = 'UP' }
}
function Start-Sleep { }
$variables = @{
    IMAGE_DIGEST = 'sha256:' + ('a' * 64)
    ECR_REPOSITORY_URL = '123456789012.dkr.ecr.us-east-1.amazonaws.com/oficina'
    DB_ENDPOINT = 'example.invalid'
    DB_SECRET_ARN = 'arn:aws:secretsmanager:us-east-1:123456789012:secret:db-test'
    JWT_SECRET_ARN = 'arn:aws:secretsmanager:us-east-1:123456789012:secret:jwt-test'
    STAFF_SECRET_ARN = 'arn:aws:secretsmanager:us-east-1:123456789012:secret:staff-test'
    API_BASE_URL = 'https://example.invalid'
    RELEASE_SHA = 'a' * 40
}
$saved = @{}
try {
    foreach ($key in $variables.Keys) {
        $saved[$key] = [Environment]::GetEnvironmentVariable($key)
        [Environment]::SetEnvironmentVariable($key, $variables[$key])
    }
    foreach ($case in @('success-first','helm-fails-first','smoke-fails-first','smoke-fails-existing','inspection-fails','existing-unhealthy')) {
        $global:deliveryTestScenario = $case
        $global:deliveryTestCalls.Clear()
        $failed = $false
        $failure = ''
        try { & (Join-Path $PSScriptRoot 'deploy-release.ps1') -Environment hml } catch { $failed = $true; $failure = $_.Exception.Message }
        if ($failed -ne ($case -ne 'success-first')) { throw "Unexpected result for ${case}: $failure" }
        $record = $global:deliveryTestCalls -join "`n"
        switch ($case) {
            'success-first' { if ($record -notmatch 'python .*cleanup-hooks.py') { throw 'Successful release did not clean obsolete hooks' } }
            'helm-fails-first' {
                if ($record -notmatch 'helm uninstall .*--ignore-not-found' -or $record -notmatch 'kubectl -n hml delete .*oficina.hook-resource=true') { throw 'Failed inaugural Helm install did not clean scoped hooks' }
            }
            'smoke-fails-first' { if ($record -notmatch 'helm uninstall' -or $record -notmatch 'kubectl -n hml delete') { throw 'Failed inaugural health check did not clean up' } }
            'smoke-fails-existing' {
                if ($record -notmatch 'helm rollback oficina-hml 3 ' -or $record -match 'helm uninstall') { throw 'External health failure did not restore previous revision safely' }
            }
            'inspection-fails' { if ($record -match 'helm upgrade') { throw 'Deployment continued after failed history inspection' } }
            'existing-unhealthy' { if ($record -match 'helm upgrade') { throw 'Deployment continued with no healthy rollback revision' } }
        }
    }
    Write-Output 'Delivery recovery behavior passed: 6 scenarios'
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key]) }
}

