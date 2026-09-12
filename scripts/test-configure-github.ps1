[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'configure-github.ps1'

if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw 'scripts/configure-github.ps1 does not exist'
}

$source = Get-Content -LiteralPath $scriptPath -Raw

$requiredLiterals = @(
    'soat-oficina-infra-k8s',
    'soat-oficina-infra-db',
    'soat-oficina-auth',
    'soat-oficina-app',
    'soat-architecture',
    'soat-protection-',
    'required_approving_review_count',
    'required_status_checks',
    'non_fast_forward',
    'deletion',
    'pull_request',
    'hml',
    'prod',
    'terraform / validate',
    'terraform / security',
    'auth / node-check',
    'auth / terraform',
    'auth / security',
    'app / backend',
    'app / image',
    'app / helm-smoke',
    'app / security',
    "'remote', 'set-url', 'origin'",
    'git@github.com:$appRepository.git'
)

foreach ($literal in $requiredLiterals) {
    if (-not $source.Contains($literal)) {
        throw "required GitHub policy literal missing: $literal"
    }
}

if ($source -notmatch 'required_approving_review_count\s*=\s*0') {
    throw 'pull request rules must require zero human approvals'
}

if ($source -notmatch "@\('hml',\s*'prod'\)") {
    throw 'both GitHub Environments must be configured'
}

if ($source -notmatch "(-Method\s+'PATCH'|--method',\s*'PATCH')" -or
    $source -notmatch "(-Method\s+'POST'|--method',\s*'POST')") {
    throw 'rulesets must be updated idempotently instead of duplicated'
}

if ($source -match 'AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|AWS_SESSION_TOKEN') {
    throw 'AWS credential variable found in GitHub configuration'
}

if ($source -match '(?<!\d)\d{12}(?!\d)') {
    throw 'literal AWS account ID found in GitHub configuration'
}

Write-Output 'GitHub configuration policy PASSED.'
