# Contratos de integração da Fase 3

Este documento congela as interfaces entre `soat-oficina-infra-k8s`,
`soat-oficina-infra-db`, `soat-oficina-auth` e `soat-oficina-app`. Metadados não
secretos atravessam repositórios por outputs de Terraform, remote state ou
variáveis de GitHub Environment. Valores secretos nunca atravessam essa
fronteira: os workloads recebem somente ARNs e resolvem o valor em runtime pelas
integrações suportadas pela AWS.

## Outputs e inputs

| Name | Owner repository | Consumer repository | Terraform type | Sensitive | Environment scope |
|---|---|---|---|---|---|
| `state_bucket_name` | `soat-oficina-infra-k8s` bootstrap | `soat-oficina-infra-k8s`, `soat-oficina-infra-db`, `soat-oficina-auth` | `string` | No | Shared |
| `vpc_id` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db`, `soat-oficina-auth` | `string` | No | Shared |
| `public_subnet_ids` | `soat-oficina-infra-k8s` | delivery verification | `list(string)` | No | Shared |
| `private_subnet_ids` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db`, `soat-oficina-auth` | `list(string)` | No | Shared |
| `cluster_name` | `soat-oficina-infra-k8s` | `soat-oficina-app` | `string` | No | Shared cluster |
| `node_security_group_id` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db` | `string` | No | Shared |
| `lambda_security_group_id` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db`, `soat-oficina-auth` | `string` | No | Shared |
| `ecr_repository_url` | `soat-oficina-infra-k8s` | `soat-oficina-app` | `string` | No | Shared registry |
| `jwt_secret_arn` | `soat-oficina-infra-k8s` | `soat-oficina-auth`, `soat-oficina-app` | `string` | Sensitive metadata only | Shared secret, isolated by the JWT `env` claim |
| `staff_secret_arns` | `soat-oficina-infra-k8s` | `soat-oficina-app` | `map(string)` | Sensitive metadata only | One secret per environment; five staff password fields |
| `app_pod_identity_role_name` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db` | `string` | No | Shared role |
| `hml_listener_arn` | `soat-oficina-infra-k8s` | `soat-oficina-auth` | `string` | No | hml |
| `prod_listener_arn` | `soat-oficina-infra-k8s` | `soat-oficina-auth` | `string` | No | prod |
| `hml_target_group_arn` | `soat-oficina-infra-k8s` | `soat-oficina-app`, delivery verification | `string` | No | hml |
| `prod_target_group_arn` | `soat-oficina-infra-k8s` | `soat-oficina-app`, delivery verification | `string` | No | prod |
| `alerts_topic_arn` | `soat-oficina-infra-k8s` | `soat-oficina-infra-db`, `soat-oficina-auth` | `string` | No | Shared |
| `github_deploy_role_arns` | `soat-oficina-infra-k8s` | all four repositories | `map(map(string))` | No | hml and prod for each repository |
| `database_endpoint` | `soat-oficina-infra-db` | `soat-oficina-auth`, `soat-oficina-app` | `string` | Sensitive metadata only | Shared instance |
| `database_port` | `soat-oficina-infra-db` | `soat-oficina-auth`, `soat-oficina-app` | `number` | No | Shared, value `5432` |
| `database_name` | `soat-oficina-infra-db` | `soat-oficina-auth`, `soat-oficina-app` | `string` | No | Shared, value `oficina` |
| `master_secret_arn` | `soat-oficina-infra-db` | `soat-oficina-auth`, `soat-oficina-app` | `string` | Sensitive metadata only | Shared secret with runtime retrieval |
| `database_kms_key_arn` | `soat-oficina-infra-db` | `soat-oficina-auth` | `string` | No | RDS managed credential key metadata |
| `rds_security_group_id` | `soat-oficina-infra-db` | delivery verification | `string` | No | Shared |
| `hml_api_url` | `soat-oficina-auth` | `soat-oficina-app`, Postman and delivery verification | `string` | No | hml |
| `prod_api_url` | `soat-oficina-auth` | `soat-oficina-app` and delivery verification | `string` | No | prod |
| `image.repository` | `soat-oficina-app` workflow | `soat-oficina-app` Helm chart | `string` | No | Shared registry |
| `image.digest` | `soat-oficina-app` workflow | `soat-oficina-app` Helm chart | `string` | No | Exact hml digest promoted to prod |
| `db.endpoint` | `soat-oficina-infra-db` | `soat-oficina-app` Helm chart | `string` | Sensitive metadata only | Shared instance |
| `db.secretArn` | `soat-oficina-infra-db` | `soat-oficina-app` Helm chart | `string` | Sensitive metadata only | Shared secret with runtime retrieval |
| `jwt.secretArn` | `soat-oficina-infra-k8s` | `soat-oficina-app` Helm chart | `string` | Sensitive metadata only | Shared secret, environment-bound tokens |
| `staff.secretArn` | `soat-oficina-infra-k8s` | `soat-oficina-app` Helm chart | `string` | Sensitive metadata only | Selected environment's staff secret |
| `environment` | GitHub branch and Environment | `soat-oficina-app` Helm chart | `string` constrained to `hml` or `prod` | No | One selected environment |
| `namespace` | `soat-oficina-app` workflow | `soat-oficina-app` Helm release | `string` | No | `hml` or `prod` |

The `github_deploy_role_arns` map contains exactly eight
repository-and-environment-scoped role ARNs: one role for each combination of
the four repositories with `hml` or `prod`. A workflow may assume only the role
published in its own GitHub Environment.

## Remote state

The bootstrap output `state_bucket_name` becomes `TF_STATE_BUCKET`. The shared
foundation owns the key `infra-k8s/terraform.tfstate`; the database owns
`infra-db/terraform.tfstate`. The database reads foundation metadata, and the
authentication infrastructure reads foundation and database metadata. Remote
state consumers use `us-east-1` and never read a secret value from Terraform
state.

The application does not consume Terraform state directly. Its workflows use
GitHub Environment variables populated from non-secret outputs after an
authorized apply. The chart receives secret ARNs and the Kubernetes Secrets
Store integration retrieves values only inside the workload.

## GitHub Environment variables

| Name | Location | Meaning |
|---|---|---|
| `AWS_REGION` | all `hml` and `prod` environments | Fixed deployment region `us-east-1` |
| `AWS_ROLE_ARN` | each `hml` and `prod` environment | Repository- and environment-scoped OIDC role |
| `TF_STATE_BUCKET` | infrastructure repository environments | Encrypted shared Terraform state bucket |
| `EKS_CLUSTER_NAME` | app `hml` and `prod` | Stable cluster name `soat-oficina-eks` |
| `ECR_REPOSITORY_URL` | app `hml` and `prod` | Immutable application registry output |
| `API_BASE_URL` | app and auth `hml` and `prod` | API Gateway stage URL used by smoke tests |
| `DB_ENDPOINT`, `DB_SECRET_ARN` | app `hml` and `prod` | RDS endpoint and managed credentials ARN |
| `JWT_SECRET_ARN`, `STAFF_SECRET_ARN` | app `hml` and `prod` | Shared JWT ARN and environment-specific staff ARN |
| `SMOKE_ACTIVE_CPF`, `SMOKE_BLOCKED_CPF`, `SMOKE_UNKNOWN_CPF`, `SMOKE_OTHER_ACTIVE_CPF`, `SMOKE_TRACKING_CODE` | auth `hml` and `prod` | Synthetic acceptance fixtures only; never real customer data |
| `OTHER_API_URL` | auth `prod` | hml gateway for rejecting cross-environment token replay |

These locations store no GitHub Environment secrets. Authentication uses GitHub
OIDC and `AWS_ROLE_ARN`; AWS credentials are short-lived and are not committed or
persisted as repository variables.

## Compatibility rules

- Output names and Terraform types in this document are public interfaces.
- Additive outputs are allowed; renaming, deleting or changing a type requires a
  coordinated change in every listed consumer.
- `hml` and `prod` remain isolated by namespace, database schema, API stage,
  deployment role and JWT environment claim.
- Image promotion copies the exact tested digest from `hml` to `prod`; production
  never rebuilds the image.
- Secret values are neither Terraform outputs nor GitHub variables. Only ARNs may
  cross repository boundaries.
