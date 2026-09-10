# Componentes da Fase 3

```mermaid
flowchart LR
  Browser[Frontend React] --> Gateway[API Gateway HTTP API hml / prod]
  Gateway -->|POST /auth/token| Auth[Lambda autenticação CPF]
  Gateway -->|rotas CUSTOMER| Authorizer[Lambda authorizer]
  Authorizer -->|JWT autorizado| Gateway
  Gateway --> Link[VPC Link compartilhado]
  Link --> NLB[NLB interno: listeners por ambiente]
  NLB --> EKS[EKS: namespaces hml e prod]
  EKS -->|TLS verify-full| DB[RDS PostgreSQL: schemas hml e prod]
  Auth -->|consulta ACTIVE por CPF / TLS| DB
  SM[Secrets Manager] -->|CSI e Pod Identity| EKS
  SM -->|runtime SDK| Auth
  SM -->|runtime SDK| Authorizer
  EKS --> CW[CloudWatch: logs, EMF, Application Signals e traces]
  Auth --> CW
  Authorizer --> CW
  Canary[Synthetics health] --> Gateway
  CW --> SNS[SNS alertas com KMS]
```

A API de staff autentica por usuário/senha no Spring; rotas de cliente passam pelo authorizer e pelo decoder Spring. A autorização de propriedade da OS pertence à aplicação, usando `sub=UUID`. Nenhum consumidor recebe credenciais via Terraform output; apenas ARNs de recursos atravessam repositórios. Os serviços compartilhados reduzem custo da demonstração, conforme [ADR](../adrs/0001-ambientes-compartilhados.md).
