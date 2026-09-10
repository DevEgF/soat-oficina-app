<#
.SYNOPSIS
    Verifica os pre-requisitos locais da Fase 3 sem exibir credenciais.

.DESCRIPTION
    Valida ferramentas, versoes minimas e sessoes locais necessarias para a
    Task 0. A consulta de identidade AWS solicita somente o ARN; configuracoes
    de credenciais e valores secretos nunca sao lidos ou impressos.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$script:Failures = New-Object 'System.Collections.Generic.List[string]'
$script:Versions = [ordered]@{}

function Write-Pass {
    param([Parameter(Mandatory = $true)][string]$Message)
    Write-Output "[PASS] $Message"
}

function Write-Failure {
    param([Parameter(Mandatory = $true)][string]$Message)
    $script:Failures.Add($Message)
    Write-Output "[FAIL] $Message"
}

function Find-Executable {
    param([Parameter(Mandatory = $true)][string]$Name)

    $command = Get-Command -Name $Name -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $command) {
        return $command.Source
    }

    if ($Name -eq 'aws') {
        $candidates = @()
        if ($env:LOCALAPPDATA) {
            $candidates += Join-Path $env:LOCALAPPDATA 'Programs\Amazon\AWSCLIV2\aws.exe'
        }
        if ($env:ProgramFiles) {
            $candidates += Join-Path $env:ProgramFiles 'Amazon\AWSCLIV2\aws.exe'
        }

        foreach ($candidate in $candidates) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf -ErrorAction SilentlyContinue) {
                return $candidate
            }
        }
    }

    return $null
}

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = & $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output = (@($lines) -join [Environment]::NewLine).Trim()
    }
}

function Get-SemanticVersion {
    param([Parameter(Mandatory = $true)][string]$Text)

    $match = [regex]::Match($Text, '(?<!\d)(\d+\.\d+(?:\.\d+)?)(?!\d)')
    if (-not $match.Success) {
        return $null
    }
    return [version]$match.Groups[1].Value
}

function Test-Tool {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$DisplayName,
        [Parameter(Mandatory = $true)][string[]]$VersionArguments,
        [scriptblock]$VersionRule,
        [string]$VersionRequirement
    )

    $executable = Find-Executable -Name $Name
    if (-not $executable) {
        Write-Failure "$DisplayName nao encontrado."
        return
    }

    $result = Invoke-External -Executable $executable -Arguments $VersionArguments
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Output)) {
        Write-Failure "$DisplayName encontrado, mas a versao nao pode ser consultada."
        return
    }

    $version = Get-SemanticVersion -Text $result.Output
    if ($null -eq $version) {
        Write-Failure "$DisplayName retornou uma versao irreconhecivel."
        return
    }

    if ($null -ne $VersionRule -and -not (& $VersionRule $version)) {
        Write-Failure "$DisplayName $version nao atende ao requisito $VersionRequirement."
        return
    }

    $script:Versions[$DisplayName] = $version.ToString()
    Write-Pass "$DisplayName $version"
}

function Test-CommandSucceeded {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$SuccessMessage,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )

    $result = Invoke-External -Executable $Executable -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        Write-Failure $FailureMessage
        return
    }

    Write-Pass $SuccessMessage
}

function Test-Checkov {
    $checkov = Find-Executable -Name 'checkov'
    if ($checkov) {
        $result = Invoke-External -Executable $checkov -Arguments @('--version')
        if ($result.ExitCode -eq 0) {
            $version = Get-SemanticVersion -Text $result.Output
            if ($null -ne $version) {
                $script:Versions['Checkov'] = $version.ToString()
                Write-Pass "Checkov $version"
                return
            }
        }
    }

    # Checkov is packaged as a Python script rather than a Windows console
    # executable. The uv launcher can therefore require its isolated Python.
    $uv = Find-Executable -Name 'uv'
    if ($uv) {
        $result = Invoke-External -Executable $uv -Arguments @(
            'tool', 'run', '--offline', '--from', 'checkov', 'checkov', '--version'
        )
        if ($result.ExitCode -eq 0) {
            $version = Get-SemanticVersion -Text $result.Output
            if ($null -ne $version) {
                $script:Versions['Checkov'] = $version.ToString()
                Write-Pass "Checkov $version"
                return
            }
        }
    }

    Write-Failure 'Checkov nao pode ser executado pelo ambiente uv instalado.'
}

Write-Output 'Phase 3 prerequisite gate'
Write-Output '========================='

Test-Tool -Name 'git' -DisplayName 'Git' -VersionArguments @('--version')
Test-Tool -Name 'gh' -DisplayName 'GitHub CLI' -VersionArguments @('--version')
Test-Tool -Name 'aws' -DisplayName 'AWS CLI' -VersionArguments @('--version')
Test-Tool -Name 'terraform' -DisplayName 'Terraform' -VersionArguments @('version') `
    -VersionRule { param($version) $version -ge [version]'1.11' } -VersionRequirement '>= 1.11'
Test-Tool -Name 'tflint' -DisplayName 'TFLint' -VersionArguments @('--version')
Test-Checkov
Test-Tool -Name 'kubectl' -DisplayName 'kubectl' -VersionArguments @('version', '--client')
Test-Tool -Name 'helm' -DisplayName 'Helm' -VersionArguments @('version', '--short')
Test-Tool -Name 'docker' -DisplayName 'Docker CLI' -VersionArguments @('--version')
Test-Tool -Name 'node' -DisplayName 'Node.js' -VersionArguments @('--version') `
    -VersionRule { param($version) $version.Major -in @(22, 24) } -VersionRequirement '22.x ou 24.x'
Test-Tool -Name 'java' -DisplayName 'Java' -VersionArguments @('-version') `
    -VersionRule { param($version) $version.Major -ge 17 } -VersionRequirement '>= 17'
Test-Tool -Name 'python' -DisplayName 'Python' -VersionArguments @('--version')
Test-Tool -Name 'pwsh' -DisplayName 'PowerShell' -VersionArguments @('--version') `
    -VersionRule { param($version) $version.Major -ge 7 } -VersionRequirement '>= 7'

$docker = Find-Executable -Name 'docker'
if ($docker) {
    Test-CommandSucceeded -Executable $docker -Arguments @('info') `
        -SuccessMessage 'Docker Engine respondeu.' `
        -FailureMessage 'Docker CLI esta instalado, mas o Docker Engine nao respondeu.'
}

$gh = Find-Executable -Name 'gh'
if ($gh) {
    Test-CommandSucceeded -Executable $gh -Arguments @('auth', 'status') `
        -SuccessMessage 'GitHub CLI esta autenticado.' `
        -FailureMessage 'GitHub CLI nao esta autenticado.'
}

$aws = Find-Executable -Name 'aws'
if ($aws) {
    $identity = Invoke-External -Executable $aws -Arguments @(
        'sts', 'get-caller-identity', '--profile', 'oficina-admin', '--query', 'Arn', '--output', 'text'
    )
    if ($identity.ExitCode -ne 0 -or $identity.Output -notmatch '^arn:[^\s]+$') {
        Write-Failure 'AWS STS nao confirmou o perfil oficina-admin.'
    }
    else {
        Write-Pass "AWS identity ARN: $($identity.Output)"
    }

    $region = Invoke-External -Executable $aws -Arguments @(
        'configure', 'get', 'region', '--profile', 'oficina-admin'
    )
    if ($region.ExitCode -ne 0 -or $region.Output -ne 'us-east-1') {
        Write-Failure 'O perfil oficina-admin nao esta configurado para us-east-1.'
    }
    else {
        Write-Pass 'AWS region: us-east-1'
    }
}

Write-Output ''
if ($script:Failures.Count -gt 0) {
    Write-Output "Prerequisite gate FAILED ($($script:Failures.Count) failure(s))."
    exit 1
}

Write-Output "Prerequisite gate PASSED ($($script:Versions.Count) tools verified)."
exit 0
