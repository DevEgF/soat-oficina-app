# Autenticação e autorização do cliente

```mermaid
sequenceDiagram
  actor Cliente
  participant UI as Frontend
  participant API as API Gateway
  participant Auth as Lambda Auth
  participant DB as RDS/schema do ambiente
  participant Guard as Lambda Authorizer
  participant App as Spring
  Cliente->>UI: CPF e código da OS
  UI->>API: POST /auth/token {cpf}
  API->>Auth: Payload validado
  Auth->>DB: Consulta parametrizada por CPF
  DB-->>Auth: UUID + status
  alt ACTIVE
    Auth-->>UI: 200 JWT CUSTOMER (900s), Cache-Control no-store
    UI->>API: GET /api/customer/os/acompanhar?codigo + Bearer
    API->>Guard: Validar HS256, iss, aud, env, exp, scope e UUID
    Guard-->>API: Autorização
    API->>App: Bearer + X-Correlation-Id
    App->>App: Validar JWT novamente e UUID proprietário
    App-->>UI: OS do próprio cliente; outra propriedade retorna 404
  else BLOCKED ou inexistente
    Auth-->>UI: 401 com o mesmo corpo genérico
  end
```

CPF inválido retorna 400 antes da consulta. Ausência do Bearer na rota do Gateway retorna 401; token presente mas rejeitado pelo authorizer retorna 403. O decoder direto do Spring retorna 401 para JWT inválido e 403 para escopo incompatível. Tokens hml não são aceitos em prod. O frontend mantém o token somente na memória e exige nova autenticação após a expiração.
