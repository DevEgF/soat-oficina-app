# Phase 3 Delivery Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use `$subagent-driven-development` (recommended) or `$executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar a Fase 3 até 15/09/2026 com quatro repositórios públicos, homologação e produção na AWS, automação CI/CD, observabilidade, documentação e vídeo demonstrável, trabalhando sozinho por cerca de duas horas por dia.

**Architecture:** A execução segue dependências explícitas: fundação compartilhada no EKS, RDS compartilhado com schemas isolados, aplicação em namespaces hml/prod, autenticação CPF serverless e exposição privada pelo API Gateway. Cada etapa produz contratos versionados e uma evidência verificável antes da próxima começar.

**Tech Stack:** GitHub CLI e Actions, Terraform, AWS CLI, EKS 1.35, RDS PostgreSQL 16, Lambda Node.js/TypeScript, Kotlin 2.4.10, Spring Boot 4.1.1, Helm, Postman/Newman, CloudWatch e ADOT.

**Spec:** `docs/superpowers/specs/2026-08-31-fase-3-aws-design.md`

## Global Constraints

- Data final: 15/09/2026; orçamento de trabalho: duas horas por dia.
- Região AWS: `us-east-1`; perfil local de bootstrap: `oficina-admin`.
- Custo ativo alvo: até US$ 20 durante a implementação; a estimativa é conferida antes de cada apply.
- Quatro repositórios públicos: `soat-oficina-infra-k8s`, `soat-oficina-infra-db`, `soat-oficina-auth` e `soat-oficina-app`.
- Pull Request obrigatório em `develop` e `main`, com zero aprovações humanas por ser trabalho individual e todos os checks obrigatórios verdes.
- `develop` publica em `hml`; `main` publica em `prod`; produção sempre recebe o mesmo artefato imutável validado em homologação.
- Nenhuma access key da AWS, senha, CPF ou token é persistido no GitHub, Terraform output, Postman export, log ou vídeo.
- Nenhum `terraform apply`, deploy ou destruição é executado durante Pull Request.
- Commits são pequenos e acompanham os commits definidos nos quatro planos técnicos.

---

## File Map

| Arquivo | Responsabilidade |
|---|---|
| `docs/superpowers/plans/2026-08-31-infra-k8s.md` | fundação AWS, EKS, rede, NLB, OIDC e custo |
| `docs/superpowers/plans/2026-08-31-infra-db.md` | RDS, schemas, acesso e alarmes |
| `docs/superpowers/plans/2026-08-31-auth-serverless.md` | autenticação CPF, JWT e API Gateway |
| `docs/superpowers/plans/2026-08-31-app-eks.md` | aplicação, segurança, Helm, CI/CD e evidências |
| `docs/architecture/integration-contracts.md` | contrato versionado entre os quatro repositórios |
| `scripts/configure-github.ps1` | repositórios, ambientes e regras de proteção |
| `scripts/capture-evidence.ps1` | coleta somente de metadados não sensíveis para a entrega |
| `docs/delivery/fase-3-checklist.md` | gate único de aceite e links da entrega |
| `docs/delivery/video-script.md` | roteiro reproduzível do vídeo |

### Task 0: Install and verify the local toolchain

**Files:**
- Create in app repository: `scripts/test-prerequisites.ps1`
- Create in app repository: `docs/delivery/tool-versions.md`

**Interfaces:**
- Requires: Windows PowerShell with administrator approval only for Chocolatey packages.
- Produces: a version inventory with no credentials or machine-specific paths.

- [ ] **Step 1: Write the failing prerequisite check**

The script checks Git, GitHub CLI, AWS CLI, Terraform 1.11 or newer, TFLint, Checkov, kubectl, Helm, Docker, Node 22 or 24, Java 17 or newer and Python. It also executes `docker info`, `gh auth status` and `aws sts get-caller-identity --profile oficina-admin`; it prints only the AWS ARN and never credential configuration.

- [ ] **Step 2: Run and record the current failures**

Run: `powershell -File scripts/test-prerequisites.ps1`

Expected on the 31/08 audit: GitHub CLI, Terraform, TFLint, Checkov and Helm are missing; Docker, kubectl, Node 24, Java 21, Git and Python are present.

- [ ] **Step 3: Install the missing tools after OS approval**

Run in an elevated PowerShell:

```powershell
choco install gh terraform tflint kubernetes-helm powershell-core -y
uv tool install checkov
```

Close and reopen the terminal once so PATH changes apply. The existing Agent Toolkit AWS CLI installation and profile are preserved.

- [ ] **Step 4: Authenticate and verify**

```powershell
gh auth login --web --git-protocol https
powershell -File scripts/test-prerequisites.ps1
```

Write the observed tool versions to `docs/delivery/tool-versions.md`. On local Windows commands, call `npm.cmd` if the Windows execution policy blocks `npm.ps1`; GitHub Linux runners continue using `npm`.

- [ ] **Step 5: Commit**

```bash
git add scripts/test-prerequisites.ps1 docs/delivery/tool-versions.md
git commit -m "chore: verify phase three toolchain"
```

### Task 1: Create the four-repository GitHub topology

**Files:**
- Create in app repository: `scripts/configure-github.ps1`
- Create in each new repository: `README.md`
- Create in each repository: `.github/CODEOWNERS`

**Interfaces:**
- Discovers the authenticated GitHub owner with `gh api user`; no owner name is hard-coded.
- Adds collaborator `soat-architecture` with push permission to all four repositories.
- Creates GitHub Environments `hml` and `prod` without stored secrets.

- [ ] **Step 1: Write the failing script policy test**

Create `scripts/test-configure-github.ps1` and require the script to contain all four exact repository names, `soat-architecture`, environments hml/prod, pull-request rules with zero approvals, required checks, deletion protection and force-push protection. Reject strings matching `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and literal account IDs.

- [ ] **Step 2: Run the test and confirm failure**

Run: `pwsh -File scripts/test-configure-github.ps1`

Expected: FAIL because the configuration script does not exist.

- [ ] **Step 3: Implement idempotent repository creation**

The script discovers context and renames the existing repository to the app repository:

```powershell
$owner = gh api user --jq .login
$currentRepository = gh repo view --json nameWithOwner --jq .nameWithOwner
if ($currentRepository -ne "$owner/soat-oficina-app") {
    gh repo rename soat-oficina-app --repo $currentRepository --yes
}

$repositories = @(
    'soat-oficina-infra-k8s',
    'soat-oficina-infra-db',
    'soat-oficina-auth',
    'soat-oficina-app'
)

foreach ($repository in $repositories) {
    gh repo view "$owner/$repository" *> $null
    if ($LASTEXITCODE -ne 0) {
        gh repo create "$owner/$repository" --public --description "FIAP SOAT Phase 3 - $repository"
    }
    gh api --method PUT "repos/$owner/$repository/collaborators/soat-architecture" -f permission=push
    gh api --method PUT "repos/$owner/$repository/environments/hml"
    gh api --method PUT "repos/$owner/$repository/environments/prod"
    gh api --method PATCH "repos/$owner/$repository" -F allow_merge_commit=true -F allow_squash_merge=false -F allow_rebase_merge=false -F delete_branch_on_merge=true
}
```

Use GitHub repository rulesets for `develop` and `main`. Each ruleset requires a Pull Request but sets `required_approving_review_count` to `0`, blocks deletion and force pushes, and requires the checks declared by that repository's technical plan. The script first lists rulesets and updates an existing `soat-protection-*` ruleset instead of duplicating it.

- [ ] **Step 4: Create and push the integration branch**

For each repository, initialize `main`, create `develop` from `main`, push both branches, then run the idempotent GitHub configuration script. Use `.github/CODEOWNERS` containing:

```text
* @soat-architecture
```

Run: `pwsh -File scripts/test-configure-github.ps1 && pwsh -File scripts/configure-github.ps1`

Expected: four public repository URLs, collaborator invitation pending or accepted, two protected branches and two environments per repository.

- [ ] **Step 5: Commit**

```bash
git add scripts/configure-github.ps1 scripts/test-configure-github.ps1 .github/CODEOWNERS
git commit -m "chore: configure phase three repositories"
```

### Task 2: Freeze the cross-repository contract

**Files:**
- Create in app repository: `docs/architecture/integration-contracts.md`
- Create in app repository: `scripts/test-integration-contract.ps1`
- Modify: every repository `README.md`

**Interfaces:**
- infra-k8s outputs: state bucket, VPC/subnets, cluster, ECR, JWT secret, NLB listener ARNs, Lambda/node security groups and eight GitHub role ARNs.
- infra-db outputs: endpoint, port, database name and managed-secret ARN.
- auth outputs: `hml_api_url` and `prod_api_url`.
- app inputs: image repository/digest, database endpoint/secret, JWT secret, cluster and namespace.

- [ ] **Step 1: Write the failing contract test**

The PowerShell test requires a table row for every output/input above, the owner repository, consumer repository, Terraform type, sensitivity and environment scope. It rejects unfinished-work markers, angle-bracket placeholders and AWS secret values.

- [ ] **Step 2: Run and confirm failure**

Run: `pwsh -File scripts/test-integration-contract.ps1`

- [ ] **Step 3: Document remote-state and GitHub Environment variables**

Use these exact variable names:

| Name | Location | Meaning |
|---|---|---|
| `AWS_REGION` | all hml/prod environments | `us-east-1` |
| `AWS_ROLE_ARN` | each hml/prod environment | repository- and environment-scoped OIDC role |
| `TF_STATE_BUCKET` | infra repositories | encrypted shared Terraform state bucket |
| `EKS_CLUSTER_NAME` | app hml/prod | `soat-oficina-eks` |
| `ECR_REPOSITORY_URL` | app hml/prod | immutable app registry output |
| `API_BASE_URL` | app/auth hml/prod | API Gateway stage URL for smoke tests |

The database and authentication repositories read non-secret infrastructure metadata by Terraform remote state. Workloads receive secret ARNs, then obtain values at runtime through the AWS-supported integrations; secret values never cross repository boundaries.

- [ ] **Step 4: Test and commit the contract**

Run: `pwsh -File scripts/test-integration-contract.ps1`

```bash
git add docs/architecture/integration-contracts.md scripts/test-integration-contract.ps1 README.md
git commit -m "docs: define cross repository contracts"
```

### Task 3: Build and verify the shared AWS foundation

**Files:**
- Execute: `docs/superpowers/plans/2026-08-31-infra-k8s.md`
- Record non-secret outputs in: `docs/delivery/fase-3-checklist.md`

- [ ] **Step 1: Complete all infra-k8s TDD tasks locally**

Run its Completion Gate without an apply. Resolve every validation, TFLint, Checkov and Terraform test failure before continuing.

- [ ] **Step 2: Verify cost and identity before apply**

```powershell
$aws = 'C:\Users\egito\AppData\Local\Programs\Amazon\AWSCLIV2\aws.exe'
& $aws sts get-caller-identity --profile oficina-admin
& $aws configure get region --profile oficina-admin
terraform plan -out=tfplan
python scripts/aws-cost-estimate.py
```

Gate: region is `us-east-1`, estimated 40-hour build window stays under US$ 10, and the monthly always-on estimate is recorded before approval.

- [ ] **Step 3: Bootstrap state and apply the foundation**

```powershell
terraform -chdir=bootstrap init
terraform -chdir=bootstrap apply
$stateBucket = terraform -chdir=bootstrap output -raw state_bucket_name
terraform init -backend-config="bucket=$stateBucket" -backend-config="region=us-east-1"
terraform apply tfplan
```

Never pass a secret value on the command line. Confirm the budget email subscription immediately after AWS sends it.

- [ ] **Step 4: Publish the non-secret GitHub variables**

Read `github_deploy_role_arns`, flatten the repository/environment map, and use `gh variable set AWS_ROLE_ARN --env` for all eight pairs. Publish `AWS_REGION`, `TF_STATE_BUCKET`, `EKS_CLUSTER_NAME` and `ECR_REPOSITORY_URL` to the relevant environments.

- [ ] **Step 5: Foundation acceptance gate**

Run: `kubectl get nodes && kubectl get pods -n kube-system && terraform output`

Expected: one Ready `t3.medium` node, Metrics Server, Pod Identity, CloudWatch/ADOT and Secrets Store provider healthy; two NLB target groups exist but remain unhealthy until the app is installed.

### Task 4: Provision the shared database safely

**Files:**
- Execute: `docs/superpowers/plans/2026-08-31-infra-db.md`

- [ ] **Step 1: Complete infra-db tests and security scan**

Run its Completion Gate and inspect the plan for exactly one private Single-AZ PostgreSQL instance, no public endpoint and ingress only from the approved EKS/Lambda security groups.

- [ ] **Step 2: Apply with deletion protection enabled**

Merge `develop`, allow its hml workflow to plan, then manually approve the apply job. Production does not create a second RDS instance; it adds isolated schema/credentials through the app migration contract.

- [ ] **Step 3: Verify without reading the password**

Confirm RDS state with `aws rds describe-db-instances`, secret metadata with `aws secretsmanager describe-secret`, and alarm state with `aws cloudwatch describe-alarms`. Do not run `get-secret-value` in an agent session or paste credentials into the shell.

- [ ] **Step 4: Record evidence**

Capture engine/version, private subnet group, encryption, deletion protection and secret ARN with the account number redacted. Add links to the Terraform run and green checks to the delivery checklist.

### Task 5: Deliver the authenticated hml journey

**Files:**
- Execute in parallel only where dependencies permit: `docs/superpowers/plans/2026-08-31-app-eks.md` Tasks 1-8 and `docs/superpowers/plans/2026-08-31-auth-serverless.md` Tasks 1-7.
- Create: `postman/hml.postman_environment.json`

- [ ] **Step 1: Finish application behavior locally**

Complete customer status, ownership authorization, JWT environment binding, migrations, JSON logs, EMF metrics and Helm packaging. The entire Gradle/JaCoCo gate must be green before building an image.

- [ ] **Step 2: Finish authentication locally**

Complete CPF validation, uniform 401, parameterized PostgreSQL query, 15-minute JWT and Powertools telemetry. Unit, integration, coverage, lint and Terraform gates must be green.

- [ ] **Step 3: Deploy the application to hml first**

Push the app commit SHA to ECR, resolve the digest, run the migration Job and install Helm into `hml`. Confirm NLB target group hml becomes healthy on NodePort 30080. Use `kubectl port-forward` for the first health check because API Gateway is not deployed yet.

- [ ] **Step 4: Deploy the hml authentication/API stack**

Apply the hml auth stack, which creates the Lambda authorizer/token handler, shared VPC Link and hml HTTP API targeting NLB listener 8080. Save only the returned API URL as `API_BASE_URL` in both hml environments.

- [ ] **Step 5: Run the complete hml acceptance collection**

Run Newman with a runtime-only CPF value and these assertions in order:

1. unknown CPF -> 401;
2. inactive CPF -> same 401 body;
3. active CPF -> JWT with `iss=oficina`, `aud=oficina-api`, `env=hml`, `scope=CUSTOMER` and expiry no greater than 900 seconds;
4. no token -> 401;
5. correct customer -> track and approve/reject their OS;
6. another customer -> 404;
7. staff login and protected transition still work;
8. health endpoint returns 200.

Clear the runtime token/CPF after the collection. Gate: CloudWatch shows correlated JSON logs, a trace crossing API/Lambda/EKS and the three approved business metrics without customer/request dimensions.

### Task 6: Complete CI/CD and promote the immutable release

**Files:**
- Execute: remaining CI/CD/documentation tasks in all four technical plans.
- Create: `scripts/capture-evidence.ps1`

- [ ] **Step 1: Enforce repository checks**

Run `scripts/configure-github.ps1` again after workflow names are final so the exact required checks match each repository. Open one test Pull Request per repository from a short-lived branch into `develop`; direct pushes must be rejected and checks must pass.

- [ ] **Step 2: Prove hml automatic deployment**

Merge the Pull Requests to `develop`. Confirm each applicable deployment uses the hml GitHub Environment, OIDC and concurrency lock. Capture workflow URL, commit SHA and image digest.

- [ ] **Step 3: Promote the same digest to prod**

Open `develop -> main` Pull Requests. The app workflow must accept the already-scanned ECR digest from hml; it must not rebuild source. Apply database migrations before switching prod pods, install the app in namespace `prod`, then deploy prod API Gateway against NLB listener 8081.

- [ ] **Step 4: Run prod smoke and rollback proof**

Run health, auth, track and one non-destructive staff read. Then deploy the prior healthy Helm revision with the manual rollback workflow, verify health, and redeploy the approved digest. Capture Helm revision numbers and GitHub run URLs; do not display CPF or tokens.

- [ ] **Step 5: Collect sanitized evidence**

`scripts/capture-evidence.ps1` may call only metadata/list/describe APIs. It writes JSON under `docs/delivery/evidence/`, redacts 12-digit account IDs and rejects keys matching `password|secretString|accessToken|cpf|authorization`. The script must fail closed if any sensitive key remains.

### Task 7: Package documentation and video evidence

**Files:**
- Create: `docs/delivery/fase-3-checklist.md`
- Create: `docs/delivery/video-script.md`
- Modify: root `README.md`

- [ ] **Step 1: Build the rubric checklist**

Map every PDF requirement to one of: repository/file, automated check, AWS screenshot/metadata, Postman request or video timestamp. Include authentication, database, Kubernetes, CI/CD, logs/traces/metrics, API documentation and collaborator access.

- [ ] **Step 2: Write the 12-minute video script**

Use this fixed sequence:

1. `00:00-00:45` challenge and four repositories;
2. `00:45-02:00` architecture and environment isolation;
3. `02:00-03:15` Terraform, EKS, RDS and cost controls;
4. `03:15-05:15` CPF authentication and security failures;
5. `05:15-07:30` complete customer/staff OS flow;
6. `07:30-09:00` CI/CD, immutable digest and rollback;
7. `09:00-10:30` logs, trace and business metrics;
8. `10:30-11:30` Swagger/Postman and tests;
9. `11:30-12:00` repository links, collaborator and cleanup plan.

- [ ] **Step 3: Run the evidence policy test**

Create `scripts/test-delivery-evidence.ps1` to require all rubric rows, reject unfinished-work markers and sensitive-key patterns, and confirm every repository URL uses the discovered owner and exact approved repository names.

Run: `pwsh -File scripts/test-delivery-evidence.ps1`

- [ ] **Step 4: Record and verify**

Record at 1080p with browser zoom readable, AWS account ID hidden and terminal history cleared of tokens. Watch the exported video once at normal speed and once at 1.5x; verify audio, links and timestamps before submitting.

- [ ] **Step 5: Commit final evidence**

```bash
git add README.md docs/delivery scripts/capture-evidence.ps1 scripts/test-delivery-evidence.ps1
git commit -m "docs: package phase three delivery evidence"
```

### Task 8: Control spend and tear down after grading

**Files:**
- Modify: `docs/delivery/fase-3-checklist.md`

- [ ] **Step 1: Check cost at the start and end of each session**

Use Cost Explorer grouped by service, Budgets forecast and the deterministic estimate script. Stop and investigate if forecast exceeds US$ 20 or an unplanned NAT Gateway, second EKS cluster, second RDS instance or public IPv4 allocation appears.

- [ ] **Step 2: Keep resources only through the evaluation window**

When not demonstrating, scale hml/prod Deployments to zero if no validation is pending. The EKS control plane, node and RDS continue charging, so schedule infrastructure destruction immediately after the evaluator confirms receipt.

- [ ] **Step 3: Destroy in dependency order after explicit confirmation**

Run protected manual workflows in this order: auth prod, auth hml, app prod, app hml, infra-db, infra-k8s. Before database destroy, create the final snapshot named with the UTC date and record its ARN. Keep the tiny versioned Terraform state bucket until grading is closed; its `prevent_destroy` guard remains enabled.

- [ ] **Step 4: Verify zero active compute**

List EKS clusters, RDS instances, load balancers, NAT Gateways, EC2 instances, Lambda functions and VPC endpoints tagged `Project=soat-oficina`. Expected: none. Check Cost Explorer again the following day for delayed usage data.

## Daily Schedule: 01/09 to 15/09

| Date | Two-hour outcome | Exit gate |
|---|---|---|
| 01/09 | local toolchain, repositories, collaborator, branches and contracts | tool check plus four repository URLs |
| 02/09 | infra-k8s scaffold, network and tests | Terraform validation green |
| 03/09 | EKS, add-ons, NLB, OIDC and budget | reviewed plan below cost cap |
| 04/09 | apply foundation and publish variables | Ready node and add-ons |
| 05/09 | RDS IaC, tests and apply | private encrypted DB and alarms |
| 06/09 | customer status, migration and CPF lookup | database/auth integration tests green |
| 07/09 | JWT, authorizer and customer ownership | security suite green |
| 08/09 | Helm, secrets mount and hml app | healthy hml NLB target |
| 09/09 | hml API Gateway and end-to-end flow | Newman hml collection green |
| 10/09 | logs, traces and business metrics | observability evidence captured |
| 11/09 | all four CI/CD pipelines | protected PR/develop runs green |
| 12/09 | production promotion and rollback | prod smoke and rollback proof |
| 13/09 | Swagger, Postman, diagrams and rubric map | evidence policy green |
| 14/09 | record video and repair gaps | reviewed final video |
| 15/09 | final links, cost check and submission | delivery checklist fully checked |

## Scope Recovery Rules

If a daily gate slips, preserve requirements in this order:

1. CPF Lambda authentication and authorization of the customer's own OS;
2. EKS/RDS deployment with hml/prod isolation and CI/CD;
3. logs, traces and the three business metrics;
4. automated tests, Swagger/Postman, architecture and video;
5. frontend polish beyond the minimum customer journey.

Do not recover time by removing tests, exposing RDS publicly, sharing prod/hml JWT claims, storing AWS keys, using mutable image tags or skipping the destroy/cost controls.

## Final Completion Gate

- [ ] All Completion Gates in the four technical plans pass.
- [ ] Four protected repositories are public and `soat-architecture` has access.
- [ ] hml and prod use separate namespaces, schemas, APIs and JWT environment claims.
- [ ] The exact hml image digest is promoted to prod and rollback is proven.
- [ ] The sanitized rubric checklist has no missing row or sensitive data.
- [ ] Video and repository links open in an anonymous browser session.
- [ ] Current cost, forecast and teardown date are recorded.
