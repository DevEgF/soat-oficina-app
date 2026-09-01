# CPF Authentication and API Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `$subagent-driven-development` (recommended) or `$executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entregar autenticação serverless por CPF, authorizers JWT e duas entradas API Gateway privadas para hml/prod.

**Architecture:** Duas cópias pequenas de cada Lambda recebem `ENVIRONMENT=hml|prod`, compartilham código, segredo JWT e RDS, mas consultam schemas diferentes. Duas HTTP APIs usam um VPC Link compartilhado e listeners distintos do mesmo NLB; somente `/api/customer/**` recebe o authorizer do Gateway, e o Spring valida o token novamente.

**Tech Stack:** Node.js 22, TypeScript, AWS Lambda Powertools, AWS SDK for JavaScript v3, `jose`, `pg`, Vitest, esbuild, Terraform `>=1.11,<2.0`, AWS Provider `~>6.0`.

**Spec:** `docs/superpowers/specs/2026-08-31-fase-3-aws-design.md`

## Global Constraints

- CPF é normalizado para 11 dígitos e validado pelos dígitos verificadores.
- Cliente só autentica com `status=ACTIVE`.
- Cliente inexistente e bloqueado retornam o mesmo `401`.
- JWT HS256 dura 900 segundos, `iss=oficina`, `aud=oficina-api`, `scope=[CUSTOMER]`, contém `env`, nunca CPF.
- `hml` usa schema `hml` e listener 8080; `prod` usa schema `prod` e listener 8081.
- Lambdas ficam em subnets privadas, sem NAT, usando o endpoint privado do Secrets Manager.
- Segredos são buscados por ARN, mantidos em cache de memória e nunca registrados.
- Reserved concurrency: 2 por função; logs retidos por 7 dias.
- Toda query usa parâmetros; o nome do schema vem de enum interno, nunca da requisição.

---

## File Map

| Arquivo | Responsabilidade |
|---|---|
| `src/domain/cpf.ts` | normalização e validação de CPF |
| `src/domain/customer.ts` | tipos de cliente/status |
| `src/config/environment.ts` | enum hml/prod e configuração validada |
| `src/ports/*.ts` | contratos de repositório, segredo e JWT |
| `src/adapters/secretsManager.ts` | cache AWS SDK v3 |
| `src/adapters/postgresCustomerRepository.ts` | query parametrizada e TLS |
| `src/security/customerJwt.ts` | emissão e verificação JWT |
| `src/application/authenticateCustomer.ts` | caso de uso sem dependência AWS |
| `src/handlers/auth.ts` | contrato HTTP de emissão |
| `src/handlers/authorizer.ts` | Lambda REQUEST authorizer |
| `infra/*.tf` | Lambdas, APIs, VPC Link, logs, alarmes e canários |
| `openapi/fase3.yaml` | contrato público dos dois ambientes |
| `test/**/*.test.ts` | unitários, handlers e adapters |

### Task 1: Scaffold TypeScript and implement CPF validation

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `eslint.config.js`
- Create: `src/domain/cpf.ts`
- Create: `src/config/environment.ts`
- Create: `test/domain/cpf.test.ts`
- Create: `test/config/environment.test.ts`

**Interfaces:**
- Produces: `normalizeAndValidateCpf(value: string): string`.
- Produces: `parseEnvironment(value: string | undefined): 'hml' | 'prod'`.

- [ ] **Step 1: Initialize exact dependencies and scripts**

```bash
npm init -y
npm install --save-exact @aws-sdk/client-secrets-manager @aws-lambda-powertools/logger @aws-lambda-powertools/metrics @aws-lambda-powertools/tracer jose pg
npm install --save-dev --save-exact typescript vitest @vitest/coverage-v8 eslint typescript-eslint esbuild yaml @types/aws-lambda @types/node @types/pg
```

Set scripts to:

```json
{
  "type": "module",
  "engines": { "node": ">=22 <25" },
  "scripts": {
    "lint": "eslint src test",
    "typecheck": "tsc --noEmit",
    "test": "vitest run --coverage",
    "build": "node scripts/build.mjs",
    "check": "npm run lint && npm run typecheck && npm test && npm run build"
  }
}
```

Use strict Node ESM configuration:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "types": ["node", "vitest/globals"]
  },
  "include": ["src", "test", "scripts"]
}
```

`eslint.config.js` exports `typescript-eslint` recommended type-checked rules for `src/**/*.ts` and `test/**/*.ts`, ignores `dist/` and uses `tsconfig.json` as its project.

- [ ] **Step 2: Write failing CPF and environment tests**

```ts
import { describe, expect, it } from 'vitest';
import { normalizeAndValidateCpf } from '../../src/domain/cpf.js';

describe('normalizeAndValidateCpf', () => {
  it('normalizes a valid CPF', () => expect(normalizeAndValidateCpf('529.982.247-25')).toBe('52998224725'));
  it.each(['11111111111', '52998224724', '123'])('rejects %s', value => {
    expect(() => normalizeAndValidateCpf(value)).toThrow('INVALID_CPF');
  });
});
```

Environment tests accept exactly `hml` and `prod` and reject `production`, empty and undefined.

- [ ] **Step 3: Run and confirm failure**

Run: `npm test -- test/domain/cpf.test.ts test/config/environment.test.ts`

- [ ] **Step 4: Implement the pure functions**

```ts
export function normalizeAndValidateCpf(value: string): string {
  const digits = value.replace(/\D/g, '');
  if (!/^\d{11}$/.test(digits) || /^(\d)\1{10}$/.test(digits)) throw new Error('INVALID_CPF');
  const check = (length: number) => {
    const sum = digits.slice(0, length).split('').reduce((acc, n, i) => acc + Number(n) * (length + 1 - i), 0);
    const remainder = (sum * 10) % 11;
    return remainder === 10 ? 0 : remainder;
  };
  if (check(9) !== Number(digits[9]) || check(10) !== Number(digits[10])) throw new Error('INVALID_CPF');
  return digits;
}

export type Environment = 'hml' | 'prod';
export function parseEnvironment(value: string | undefined): Environment {
  if (value === 'hml' || value === 'prod') return value;
  throw new Error('INVALID_ENVIRONMENT');
}
```

- [ ] **Step 5: Run checks and commit**

Run: `npm run lint && npm run typecheck && npm test`

```bash
git add package.json package-lock.json tsconfig.json eslint.config.js src test
git commit -m "feat: validate cpf and deployment environment"
```

### Task 2: Read secrets safely and query the correct PostgreSQL schema

**Files:**
- Create: `src/domain/customer.ts`
- Create: `src/ports/customerRepository.ts`
- Create: `src/ports/secretProvider.ts`
- Create: `src/adapters/secretsManager.ts`
- Create: `src/adapters/postgresCustomerRepository.ts`
- Create: `scripts/download-rds-ca.mjs`
- Create: `test/adapters/postgresCustomerRepository.test.ts`
- Create: `test/adapters/secretsManager.test.ts`

**Interfaces:**
- Produces: `CustomerRepository.findByCpf(cpf, environment): Promise<CustomerRecord | null>`.
- Produces: `SecretProvider.getJson<T>(arn): Promise<T>`.
- Customer type: `{ id: string; status: 'ACTIVE' | 'BLOCKED' }`.

- [ ] **Step 1: Write a failing repository test with a fake Pool**

```ts
it('uses a parameter and the hml allowlisted schema', async () => {
  const query = vi.fn().mockResolvedValue({ rows: [{ id: '9a8db7aa-28ae-4fc1-b784-e6cd90364cb3', status: 'ACTIVE' }] });
  const repo = new PostgresCustomerRepository({ query } as never);
  await repo.findByCpf('52998224725', 'hml');
  expect(query).toHaveBeenCalledWith(
    'SELECT id, status FROM "hml".clientes WHERE documento = $1 LIMIT 1',
    ['52998224725'],
  );
});

it('never accepts a schema outside the Environment union', async () => {
  await expect(repo.findByCpf('52998224725', 'other' as never)).rejects.toThrow('INVALID_ENVIRONMENT');
});
```

Add a secret test proving the second call for the same ARN uses the memory cache and invokes `SecretsManagerClient.send` only once.

- [ ] **Step 2: Run and confirm failure**

Run: `npm test -- test/adapters`

- [ ] **Step 3: Implement ports, cached provider and repository**

```ts
export interface CustomerRecord { id: string; status: 'ACTIVE' | 'BLOCKED' }
export interface CustomerRepository {
  findByCpf(cpf: string, environment: Environment): Promise<CustomerRecord | null>;
}

const schemas: Record<Environment, string> = { hml: 'hml', prod: 'prod' };
export class PostgresCustomerRepository implements CustomerRepository {
  constructor(private readonly pool: Pick<Pool, 'query'>) {}
  async findByCpf(cpf: string, environment: Environment): Promise<CustomerRecord | null> {
    const schema = schemas[parseEnvironment(environment)];
    const result = await this.pool.query<CustomerRecord>(
      `SELECT id, status FROM "${schema}".clientes WHERE documento = $1 LIMIT 1`,
      [cpf],
    );
    return result.rows[0] ?? null;
  }
}
```

`SecretsManagerSecretProvider` must reject an empty `SecretString`, parse JSON, cache by ARN and never include the ARN value or secret body in an error message.

`scripts/download-rds-ca.mjs` downloads `https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem` into `certs/global-bundle.pem`; the build includes it. Pool config uses `max: 2`, `connectionTimeoutMillis: 3000`, `idleTimeoutMillis: 30000`, database `oficina`, and `ssl.ca` loaded from the bundle.

- [ ] **Step 4: Run adapter tests**

Run: `npm run typecheck && npm test -- test/adapters`

- [ ] **Step 5: Commit**

```bash
git add src scripts test certs package.json package-lock.json
git commit -m "feat: query active customers from private postgres"
```

### Task 3: Issue and verify environment-bound customer JWTs

**Files:**
- Create: `src/ports/customerJwt.ts`
- Create: `src/security/customerJwt.ts`
- Create: `test/security/customerJwt.test.ts`

**Interfaces:**
- Produces: `issue(customerId, environment): Promise<IssuedCustomerToken>`.
- Produces: `verify(token, environment): Promise<VerifiedCustomerToken>`.
- `IssuedCustomerToken = { accessToken: string; expiresIn: 900 }`.

- [ ] **Step 1: Write failing claim tests**

```ts
it('issues only approved customer claims', async () => {
  const service = new JoseCustomerJwt('test-secret-that-is-long-enough');
  const issued = await service.issue('9a8db7aa-28ae-4fc1-b784-e6cd90364cb3', 'hml');
  const token = await service.verify(issued.accessToken, 'hml');
  expect(token).toMatchObject({ sub: '9a8db7aa-28ae-4fc1-b784-e6cd90364cb3', env: 'hml', scope: ['CUSTOMER'] });
  expect(JSON.stringify(token)).not.toContain('cpf');
  expect(issued.expiresIn).toBe(900);
});

it('rejects an hml token in prod', async () => {
  const issued = await service.issue(customerId, 'hml');
  await expect(service.verify(issued.accessToken, 'prod')).rejects.toThrow('INVALID_TOKEN');
});
```

- [ ] **Step 2: Run and confirm failure**

Run: `npm test -- test/security/customerJwt.test.ts`

- [ ] **Step 3: Implement compatible HS256 derivation and claims**

```ts
const key = createHash('sha256').update(rawSecret, 'utf8').digest();
const jwt = await new SignJWT({ scope: ['CUSTOMER'], env: environment })
  .setProtectedHeader({ alg: 'HS256' })
  .setIssuer('oficina')
  .setAudience('oficina-api')
  .setSubject(customerId)
  .setIssuedAt()
  .setExpirationTime('15m')
  .sign(key);
```

Verification requires algorithm `HS256`, issuer, audience, expiration, UUID `sub`, exact environment and `CUSTOMER` scope. Map all `jose` errors to `INVALID_TOKEN` without returning the original token.

- [ ] **Step 4: Run JWT tests**

Run: `npm run typecheck && npm test -- test/security/customerJwt.test.ts`

- [ ] **Step 5: Commit**

```bash
git add src/ports/customerJwt.ts src/security/customerJwt.ts test/security
git commit -m "feat: issue environment-bound customer jwt"
```

### Task 4: Implement the authentication use case

**Files:**
- Create: `src/application/authenticateCustomer.ts`
- Create: `src/application/errors.ts`
- Create: `test/application/authenticateCustomer.test.ts`

**Interfaces:**
- Produces: `AuthenticateCustomer.execute(input): Promise<IssuedCustomerToken>`.
- Input: `{ cpf: string; environment: Environment }`.
- Errors: `MalformedCpfError`, `InvalidCustomerError`, `DependencyUnavailableError`.

- [ ] **Step 1: Write failing behavior tests**

```ts
it('issues a token for an active customer', async () => {
  repository.findByCpf.mockResolvedValue({ id: customerId, status: 'ACTIVE' });
  await expect(useCase.execute({ cpf: '529.982.247-25', environment: 'hml' }))
    .resolves.toEqual({ accessToken: 'jwt', expiresIn: 900 });
});

it.each([null, { id: customerId, status: 'BLOCKED' }])('returns the same invalid-customer error for %o', async record => {
  repository.findByCpf.mockResolvedValue(record);
  await expect(useCase.execute({ cpf: '52998224725', environment: 'hml' }))
    .rejects.toThrow(InvalidCustomerError);
});
```

- [ ] **Step 2: Run and confirm failure**

Run: `npm test -- test/application/authenticateCustomer.test.ts`

- [ ] **Step 3: Implement minimal orchestration**

Normalize CPF, call the repository once, reject missing/non-active records with the same error, issue the token using only the UUID, and wrap database/secret connectivity failures as `DependencyUnavailableError`. Do not catch `MalformedCpfError` as a dependency failure.

- [ ] **Step 4: Run application tests**

Run: `npm test -- test/application/authenticateCustomer.test.ts`

- [ ] **Step 5: Commit**

```bash
git add src/application test/application
git commit -m "feat: authenticate active customer by cpf"
```

### Task 5: Build observable Lambda handlers

**Files:**
- Create: `src/composition.ts`
- Create: `src/handlers/auth.ts`
- Create: `src/handlers/authorizer.ts`
- Create: `src/observability/requestContext.ts`
- Create: `test/handlers/auth.test.ts`
- Create: `test/handlers/authorizer.test.ts`
- Create: `scripts/build.mjs`

**Interfaces:**
- Auth response: `{ accessToken: string, tokenType: 'Bearer', expiresIn: 900 }`.
- Authorizer response: `{ isAuthorized: boolean, context: { customerId, scope, environment, requestId } }`.

- [ ] **Step 1: Write failing HTTP contract tests**

Test these cases exactly: malformed JSON -> 400, invalid CPF -> 400, missing/blocked customer -> identical 401 body `{ "message": "Unauthorized" }`, dependency failure -> 503, success -> 200 with `Cache-Control: no-store`; authorizer without bearer -> false; expired/cross-environment token -> false; valid token -> true with no CPF context.

```ts
expect(JSON.parse(response.body)).toEqual({ message: 'Unauthorized' });
expect(response.headers).toMatchObject({ 'cache-control': 'no-store', 'x-request-id': 'req-123' });
```

- [ ] **Step 2: Run and confirm failure**

Run: `npm test -- test/handlers`

- [ ] **Step 3: Implement handlers with Powertools**

Use Powertools Logger/Tracer/Metrics with service names `oficina-auth` and `oficina-authorizer`, namespace `Oficina`, and correlation from `event.requestContext.requestId`. Log only event name, environment, requestId, statusCode and duration. Emit `AuthSucceeded`, `AuthRejected`, `AuthDependencyFailure`, authorizer latency and errors without CPF/customer dimensions.

`scripts/build.mjs` creates `dist/auth/index.mjs` and `dist/authorizer/index.mjs` with esbuild, externalizes no runtime dependency, includes source maps and copies `certs/global-bundle.pem`.

- [ ] **Step 4: Run the full Node check**

Run: `npm run check`

- [ ] **Step 5: Commit**

```bash
git add src test scripts package.json package-lock.json
git commit -m "feat: expose observable auth lambda handlers"
```

### Task 6: Provision Lambdas, private APIs and VPC Link

**Files:**
- Create: `infra/versions.tf`
- Create: `infra/variables.tf`
- Create: `infra/data.tf`
- Create: `infra/iam.tf`
- Create: `infra/lambda.tf`
- Create: `infra/api-gateway.tf`
- Create: `infra/outputs.tf`
- Create: `infra/tests/routes.tftest.hcl`

**Interfaces:**
- Consumes k8s outputs: private subnets, Lambda SG, JWT secret ARN, listener ARNs.
- Consumes db outputs: endpoint metadata, database name, secret ARN.
- Produces: `hml_api_url`, `prod_api_url`, API IDs and function names.

- [ ] **Step 1: Write failing route assertions**

```hcl
mock_provider "aws" {}
run "route_contract" {
  command = plan
  variables { state_bucket = "soat-oficina-test-state" }
  override_data {
    target = data.terraform_remote_state.k8s
    values = {
      outputs = {
        private_subnet_ids       = ["subnet-11111111", "subnet-22222222"]
        lambda_security_group_id = "sg-11111111"
        jwt_secret_arn           = "arn:aws:secretsmanager:us-east-1:111122223333:secret:soat-oficina/shared/jwt"
        hml_listener_arn         = "arn:aws:elasticloadbalancing:us-east-1:111122223333:listener/net/hml/1111/2222"
        prod_listener_arn        = "arn:aws:elasticloadbalancing:us-east-1:111122223333:listener/net/prod/3333/4444"
        alerts_topic_arn         = "arn:aws:sns:us-east-1:111122223333:soat-oficina-alerts"
      }
    }
  }
  override_data {
    target = data.terraform_remote_state.db
    values = {
      outputs = {
        database_endpoint = "soat-oficina-db.abcdefghijkl.us-east-1.rds.amazonaws.com"
        database_port     = 5432
        database_name     = "oficina"
        master_secret_arn = "arn:aws:secretsmanager:us-east-1:111122223333:secret:rds-db-credentials/example"
      }
    }
  }
  assert {
    condition     = aws_apigatewayv2_route.auth["hml"].route_key == "POST /auth/token"
    error_message = "auth route changed"
  }
  assert {
    condition     = aws_apigatewayv2_route.customer["prod"].authorization_type == "CUSTOM"
    error_message = "customer route must use authorizer"
  }
  assert {
    condition     = aws_lambda_function.auth["hml"].reserved_concurrent_executions == 2
    error_message = "reserved concurrency changed"
  }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `terraform -chdir=infra init -backend=false && terraform -chdir=infra test -filter=tests/routes.tftest.hcl`

- [ ] **Step 3: Implement environment-indexed functions and exact IAM**

Create hml/prod auth and authorizer functions using runtime `nodejs22.x`, architectures `arm64`, 256 MB, timeout 5 seconds, reserved concurrency 2, private subnets and shared Lambda SG. Environment variables contain only ARNs/metadata: `ENVIRONMENT`, `DB_SECRET_ARN`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `JWT_SECRET_ARN`, and `NODE_EXTRA_CA_CERTS=/var/task/certs/global-bundle.pem`.

Grant only `secretsmanager:GetSecretValue` on the two exact secret ARNs, CloudWatch Logs and X-Ray writes, and ENI permissions required by VPC Lambda. No secret content enters Terraform outputs or Lambda variables.

- [ ] **Step 4: Implement two HTTP APIs over one VPC Link**

Create one shared `aws_apigatewayv2_vpc_link` in the two private subnets using the Lambda security group; the NLB has private-link inbound security-group evaluation disabled by the foundation plan. For each environment create an HTTP API with `$default` stage, detailed metrics, access log JSON, and routes:

```text
POST /auth/token                    -> environment auth Lambda
ANY /api/customer/{proxy+}          -> NLB listener with Lambda authorizer
ANY /api/admin/{proxy+}             -> NLB listener, Spring JWT
ANY /api/attendant/{proxy+}         -> NLB listener, Spring JWT
ANY /api/technician/{proxy+}        -> NLB listener, Spring JWT
ANY /api/warehouse/{proxy+}         -> NLB listener, Spring JWT
POST /api/public/auth/login         -> NLB listener
GET /actuator/health                -> NLB listener
GET /v3/api-docs                    -> NLB listener
ANY /v3/api-docs/{proxy+}            -> NLB listener
GET /swagger-ui.html                 -> NLB listener
ANY /swagger-ui/{proxy+}            -> NLB listener
```

Private integration settings:

```hcl
connection_type    = "VPC_LINK"
integration_type   = "HTTP_PROXY"
integration_method = "ANY"
integration_uri    = each.key == "hml" ? local.hml_listener_arn : local.prod_listener_arn
payload_format_version = "1.0"
```

Auth route throttle is 5 requests/second with burst 10. Customer authorizer uses REQUEST payload v2.0, simple responses, no authorizer result cache for the demo.

- [ ] **Step 5: Run, document outputs and commit**

Run: `terraform -chdir=infra fmt -check -recursive && terraform -chdir=infra validate && terraform -chdir=infra test`

```bash
git add infra
git commit -m "feat: provision private api and auth lambdas"
```

### Task 7: Add service dashboard, alarms and synthetic health

**Files:**
- Create: `infra/observability.tf`
- Create: `infra/canary/health.js`
- Create: `infra/tests/observability.tftest.hcl`

**Interfaces:**
- Consumes: SNS topic from infra-k8s and API URLs from Task 6.
- Produces: dashboards `soat-oficina-hml`, `soat-oficina-prod` and three alarm categories.

- [ ] **Step 1: Write failing alarm tests**

```hcl
mock_provider "aws" {}
run "observability_contract" {
  command = plan
  variables { state_bucket = "soat-oficina-test-state" }
  override_data {
    target = data.terraform_remote_state.k8s
    values = {
      outputs = {
        private_subnet_ids       = ["subnet-11111111", "subnet-22222222"]
        lambda_security_group_id = "sg-11111111"
        jwt_secret_arn           = "arn:aws:secretsmanager:us-east-1:111122223333:secret:soat-oficina/shared/jwt"
        hml_listener_arn         = "arn:aws:elasticloadbalancing:us-east-1:111122223333:listener/net/hml/1111/2222"
        prod_listener_arn        = "arn:aws:elasticloadbalancing:us-east-1:111122223333:listener/net/prod/3333/4444"
        alerts_topic_arn         = "arn:aws:sns:us-east-1:111122223333:soat-oficina-alerts"
      }
    }
  }
  override_data {
    target = data.terraform_remote_state.db
    values = {
      outputs = {
        database_endpoint = "soat-oficina-db.abcdefghijkl.us-east-1.rds.amazonaws.com"
        database_port     = 5432
        database_name     = "oficina"
        master_secret_arn = "arn:aws:secretsmanager:us-east-1:111122223333:secret:rds-db-credentials/example"
      }
    }
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.api_latency["prod"].extended_statistic == "p99"
    error_message = "latency must use p99"
  }
  assert {
    condition     = aws_cloudwatch_metric_alarm.processing_failures["prod"].treat_missing_data == "notBreaching"
    error_message = "sparse errors need notBreaching"
  }
  assert {
    condition     = aws_synthetics_canary.health["prod"].schedule[0].expression == "rate(15 minutes)"
    error_message = "canary interval changed"
  }
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `terraform -chdir=infra test -filter=tests/observability.tftest.hcl`

- [ ] **Step 3: Implement canaries, 2-of-3 alarms and dashboards**

Create one encrypted S3 artifacts bucket with public access blocked and a seven-day lifecycle, plus one canary IAM role scoped to that bucket, its log group, X-Ray and CloudWatch metrics. Use runtime `syn-nodejs-puppeteer-17.0`. Each canary calls `/actuator/health`, requires HTTP 200 and JSON `status=UP`, and does not log response bodies. Create alarms for canary failure (`breaching` on missing), API p99 latency above 2000 ms (2 of 3 one-minute periods), and `Oficina/WorkOrderProcessingFailures > 0` (`notBreaching` on missing). Dashboard widgets include alarms, API requests/4xx/5xx, p50/p95/p99, Lambda errors/duration/throttles, business metrics and a Logs Insights error table.

- [ ] **Step 4: Run observability tests**

Run: `terraform -chdir=infra test -filter=tests/observability.tftest.hcl && terraform -chdir=infra validate`

- [ ] **Step 5: Commit**

```bash
git add infra/observability.tf infra/canary infra/tests/observability.tftest.hcl
git commit -m "feat: add api health dashboards and alarms"
```

### Task 8: Add CI/CD, OpenAPI and repository runbook

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/deploy.yml`
- Create: `.github/workflows/destroy.yml`
- Create: `openapi/fase3.yaml`
- Create: `README.md`
- Create: `docs/architecture.md`
- Create: `docs/runbook.md`
- Create: `test/workflows.test.ts`

**Interfaces:**
- Required checks: `auth / node-check`, `auth / terraform`, `auth / security`.
- Deploy outputs environment URL to GitHub deployment history.

- [ ] **Step 1: Write failing workflow and OpenAPI checks**

Test that PR CI runs `npm ci`, `npm run check`, Terraform fmt/validate/test and Checkov; deploy requests OIDC, builds before Terraform, targets the GitHub Environment derived from branch, and uses concurrency per environment; destroy is manual and requires `DESTROY-soat-oficina-auth`. Parse OpenAPI and assert `/auth/token` has 200/400/401/429/503 and customer routes require bearer auth.

- [ ] **Step 2: Run and confirm failure**

Run: `npm test -- test/workflows.test.ts`

- [ ] **Step 3: Implement workflows and API contract**

Deploy order: Node check -> build zips -> OIDC -> Terraform init -> plan artifact -> apply -> read URL from Terraform output -> smoke auth with a synthetic ACTIVE CPF -> verify unauthenticated customer route returns 401. Never echo JWT; pass it between steps through a masked environment file and delete it before the job ends.

OpenAPI uses a `baseUrl` server variable whose committed default is `http://localhost:8080`; the README lists the two actual Terraform URL outputs and the Postman environment injects one at runtime. The contract documents that CPF-only auth is academic.

- [ ] **Step 4: Run all checks**

Run: `npm run check && terraform -chdir=infra fmt -check -recursive && terraform -chdir=infra validate && terraform -chdir=infra test && checkov -d infra --framework terraform --quiet`

- [ ] **Step 5: Commit**

```bash
git add .github openapi README.md docs test
git commit -m "ci: automate serverless auth and api delivery"
```

## Completion Gate

Run:

```bash
npm ci
npm run check
terraform -chdir=infra init -backend=false
terraform -chdir=infra fmt -check -recursive
terraform -chdir=infra validate
terraform -chdir=infra test
checkov -d infra --framework terraform --quiet
```

Behavioral proof after authorized deploy:

1. malformed CPF -> 400;
2. ACTIVE synthetic CPF -> JWT with no CPF claim;
3. BLOCKED and unknown CPF -> identical 401;
4. customer route without JWT -> 401;
5. hml JWT on prod -> 401;
6. valid environment JWT -> request reaches Spring;
7. requestId correlates API access log, Lambda log and Spring log.
