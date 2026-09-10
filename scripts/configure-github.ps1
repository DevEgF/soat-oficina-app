[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Invoke-GhCommand {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = & gh @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $result = [PSCustomObject]@{
        ExitCode = $exitCode
        Output   = (@($lines) -join [Environment]::NewLine).Trim()
    }

    if ($result.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "GitHub CLI command failed: gh $($Arguments[0]) $($Arguments[1])"
    }

    return $result
}

function Invoke-GhJson {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('POST', 'PUT', 'PATCH')][string]$Method,
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = $json | & gh api `
            --method $Method `
            -H 'Accept: application/vnd.github+json' `
            -H 'X-GitHub-Api-Version: 2026-03-10' `
            $Endpoint `
            --input - 2>&1 | ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "GitHub API $Method failed for $Endpoint"
    }

    return (@($lines) -join [Environment]::NewLine).Trim()
}

function Set-GitRemoteOrigin {
    param([Parameter(Mandatory = $true)][string]$Url)

    $arguments = @('remote', 'set-url', 'origin', $Url)
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $null = & git @arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw 'failed to synchronize the local origin after the GitHub repository rename'
    }
}

function Test-RepositoryExists {
    param([Parameter(Mandatory = $true)][string]$NameWithOwner)

    $result = Invoke-GhCommand -Arguments @(
        'repo', 'view', $NameWithOwner, '--json', 'nameWithOwner'
    ) -AllowFailure
    return $result.ExitCode -eq 0
}

function Ensure-CodeOwners {
    param([Parameter(Mandatory = $true)][string]$NameWithOwner)

    $file = Invoke-GhCommand -Arguments @(
        'api', "repos/$NameWithOwner/contents/.github/CODEOWNERS"
    ) -AllowFailure
    if ($file.ExitCode -eq 0) {
        return
    }

    $content = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("* @soat-architecture`n")
    )
    $null = Invoke-GhJson -Method 'PUT' `
        -Endpoint "repos/$NameWithOwner/contents/.github/CODEOWNERS" `
        -Body ([ordered]@{
            message = 'chore: add code owners'
            content = $content
            branch  = 'main'
        })
}

function Ensure-DevelopBranch {
    param([Parameter(Mandatory = $true)][string]$NameWithOwner)

    $develop = Invoke-GhCommand -Arguments @(
        'api', "repos/$NameWithOwner/git/ref/heads/develop"
    ) -AllowFailure
    if ($develop.ExitCode -eq 0) {
        return
    }

    $main = Invoke-GhCommand -Arguments @(
        'api', "repos/$NameWithOwner/git/ref/heads/main", '--jq', '.object.sha'
    )
    $null = Invoke-GhJson -Method 'POST' `
        -Endpoint "repos/$NameWithOwner/git/refs" `
        -Body ([ordered]@{
            ref = 'refs/heads/develop'
            sha = $main.Output
        })
}

function New-RulesetBody {
    param(
        [Parameter(Mandatory = $true)][string]$Branch,
        [Parameter(Mandatory = $true)][string[]]$Checks
    )

    $requiredChecks = @(
        foreach ($check in $Checks) {
            [ordered]@{ context = $check }
        }
    )

    return [ordered]@{
        name        = "soat-protection-$Branch"
        target      = 'branch'
        enforcement = 'active'
        bypass_actors = @()
        conditions = [ordered]@{
            ref_name = [ordered]@{
                include = @("refs/heads/$Branch")
                exclude = @()
            }
        }
        rules = @(
            [ordered]@{ type = 'deletion' },
            [ordered]@{ type = 'non_fast_forward' },
            [ordered]@{
                type       = 'pull_request'
                parameters = [ordered]@{
                    allowed_merge_methods              = @('merge')
                    dismiss_stale_reviews_on_push      = $false
                    require_code_owner_review          = $false
                    require_last_push_approval         = $false
                    required_approving_review_count    = 0
                    required_review_thread_resolution  = $true
                }
            },
            [ordered]@{
                type       = 'required_status_checks'
                parameters = [ordered]@{
                    do_not_enforce_on_create              = $false
                    required_status_checks                 = $requiredChecks
                    strict_required_status_checks_policy   = $true
                }
            }
        )
    }
}

function Set-RepositoryRuleset {
    param(
        [Parameter(Mandatory = $true)][string]$NameWithOwner,
        [Parameter(Mandatory = $true)][string]$Branch,
        [Parameter(Mandatory = $true)][string[]]$Checks
    )

    $name = "soat-protection-$Branch"
    $list = Invoke-GhCommand -Arguments @(
        'api', "repos/$NameWithOwner/rulesets?includes_parents=false"
    )
    $rulesets = if ([string]::IsNullOrWhiteSpace($list.Output)) {
        @()
    }
    else {
        @($list.Output | ConvertFrom-Json)
    }
    $existing = @($rulesets | Where-Object { $_.name -eq $name }) | Select-Object -First 1
    $body = New-RulesetBody -Branch $Branch -Checks $Checks

    if ($null -eq $existing) {
        $null = Invoke-GhJson -Method 'POST' `
            -Endpoint "repos/$NameWithOwner/rulesets" `
            -Body $body
    }
    else {
        $null = Invoke-GhJson -Method 'PUT' `
            -Endpoint "repos/$NameWithOwner/rulesets/$($existing.id)" `
            -Body $body
    }
}

if (-not (Get-Command gh -CommandType Application -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI is required'
}

if (-not (Get-Command git -CommandType Application -ErrorAction SilentlyContinue)) {
    throw 'Git is required'
}

$auth = Invoke-GhCommand -Arguments @('auth', 'status') -AllowFailure
if ($auth.ExitCode -ne 0) {
    throw 'GitHub CLI is not authenticated'
}

$owner = (Invoke-GhCommand -Arguments @('api', 'user', '--jq', '.login')).Output
$currentRepository = (Invoke-GhCommand -Arguments @(
    'repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner'
)).Output
$appRepository = "$owner/soat-oficina-app"

if ($currentRepository -ne $appRepository) {
    if (Test-RepositoryExists -NameWithOwner $appRepository) {
        throw "$appRepository already exists and cannot replace $currentRepository"
    }
    $null = Invoke-GhCommand -Arguments @(
        'repo', 'rename', 'soat-oficina-app', '--repo', $currentRepository, '--yes'
    )
    $currentRepository = $appRepository
}

Set-GitRemoteOrigin -Url "git@github.com:$appRepository.git"

$repositoryChecks = [ordered]@{
    'soat-oficina-infra-k8s' = @(
        'terraform / validate',
        'terraform / security'
    )
    'soat-oficina-infra-db' = @(
        'terraform / validate',
        'terraform / security'
    )
    'soat-oficina-auth' = @(
        'auth / node-check',
        'auth / terraform',
        'auth / security'
    )
    'soat-oficina-app' = @(
        'app / backend',
        'app / image',
        'app / helm-smoke',
        'app / security'
    )
}

$environments = @('hml', 'prod')

foreach ($repository in $repositoryChecks.Keys) {
    $nameWithOwner = "$owner/$repository"
    $created = $false
    if (-not (Test-RepositoryExists -NameWithOwner $nameWithOwner)) {
        $null = Invoke-GhCommand -Arguments @(
            'repo', 'create', $nameWithOwner,
            '--public',
            '--description', "FIAP SOAT Phase 3 - $repository",
            '--add-readme'
        )
        $created = $true
    }

    if ($created -and $repository -ne 'soat-oficina-app') {
        Ensure-CodeOwners -NameWithOwner $nameWithOwner
    }
    Ensure-DevelopBranch -NameWithOwner $nameWithOwner

    $null = Invoke-GhJson -Method 'PUT' `
        -Endpoint "repos/$nameWithOwner/collaborators/soat-architecture" `
        -Body ([ordered]@{ permission = 'push' })

    foreach ($environment in $environments) {
        $null = Invoke-GhJson -Method 'PUT' `
            -Endpoint "repos/$nameWithOwner/environments/$environment" `
            -Body ([ordered]@{})
    }

    $null = Invoke-GhJson -Method 'PATCH' `
        -Endpoint "repos/$nameWithOwner" `
        -Body ([ordered]@{
            allow_merge_commit     = $true
            allow_squash_merge     = $false
            allow_rebase_merge     = $false
            delete_branch_on_merge = $true
        })

    foreach ($branch in @('develop', 'main')) {
        Set-RepositoryRuleset `
            -NameWithOwner $nameWithOwner `
            -Branch $branch `
            -Checks $repositoryChecks[$repository]
    }

    $url = (Invoke-GhCommand -Arguments @(
        'repo', 'view', $nameWithOwner, '--json', 'url', '--jq', '.url'
    )).Output
    Write-Output "$repository $url"
}

Write-Output 'GitHub repository configuration completed.'
