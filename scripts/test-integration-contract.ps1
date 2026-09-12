[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$contractPath = Join-Path $PSScriptRoot '..\docs\architecture\integration-contracts.md'
$readmePath = Join-Path $PSScriptRoot '..\README.md'

if (-not (Test-Path -LiteralPath $contractPath -PathType Leaf)) {
    throw 'docs/architecture/integration-contracts.md does not exist'
}

$contract = Get-Content -LiteralPath $contractPath -Raw
$readme = Get-Content -LiteralPath $readmePath -Raw

$requiredColumns = @(
    'Name',
    'Owner repository',
    'Consumer repository',
    'Terraform type',
    'Sensitive',
    'Environment scope'
)

foreach ($column in $requiredColumns) {
    if (-not $contract.Contains($column)) {
        throw "integration contract column missing: $column"
    }
}

$requiredInterfaces = @(
    'state_bucket_name',
    'vpc_id',
    'public_subnet_ids',
    'private_subnet_ids',
    'cluster_name',
    'node_security_group_id',
    'lambda_security_group_id',
    'ecr_repository_url',
    'jwt_secret_arn',
    'app_pod_identity_role_name',
    'hml_listener_arn',
    'prod_listener_arn',
    'hml_target_group_arn',
    'prod_target_group_arn',
    'alerts_topic_arn',
    'github_deploy_role_arns',
    'database_endpoint',
    'database_port',
    'database_name',
    'master_secret_arn',
    'rds_security_group_id',
    'hml_api_url',
    'prod_api_url',
    'image.repository',
    'image.digest',
    'db.endpoint',
    'db.secretArn',
    'jwt.secretArn',
    'environment',
    'namespace'
)

foreach ($interface in $requiredInterfaces) {
    if (-not $contract.Contains("``$interface``")) {
        throw "integration contract interface missing: $interface"
    }
}

$requiredVariables = @(
    'AWS_REGION',
    'AWS_ROLE_ARN',
    'TF_STATE_BUCKET',
    'EKS_CLUSTER_NAME',
    'ECR_REPOSITORY_URL',
    'API_BASE_URL'
)

foreach ($variable in $requiredVariables) {
    if (-not $contract.Contains("``$variable``")) {
        throw "GitHub Environment variable missing: $variable"
    }
}

if ($contract -notmatch 'eight\s+repository-and-environment-scoped\s+role ARNs') {
    throw 'the eight GitHub OIDC role ARNs are not documented'
}

if ($readme -notmatch 'docs/architecture/integration-contracts\.md') {
    throw 'README does not link the integration contract'
}

$forbiddenPatterns = [ordered]@{
    'unfinished-work marker' = '(?im)\b(TODO|TBD|FIXME)\b'
    'angle-bracket placeholder' = '<[^>]+>'
    'AWS access key variable' = 'AWS_(ACCESS_KEY_ID|SECRET_ACCESS_KEY|SESSION_TOKEN)'
    'AWS access key value' = '\b(AKIA|ASIA)[A-Z0-9]{16}\b'
    'secret value assignment' = '(?im)\b(password|secretString|accessToken)\s*[:=]'
    'private key' = '-----BEGIN [A-Z ]*PRIVATE KEY-----'
}

foreach ($entry in $forbiddenPatterns.GetEnumerator()) {
    if ($contract -match $entry.Value) {
        throw "integration contract contains forbidden $($entry.Key)"
    }
}

Write-Output 'Cross-repository integration contract PASSED.'
