# RFC 0003 - Autenticação do cliente

## Decisão atual: mesmo contrato JWT no adaptador HTTP OCI

Status: Accepted - adaptador HTTP OCI com contrato acadêmico preservado, atualização de 13/09/2026.

O caso de uso de autenticação e o emissor JWT foram reaproveitados em um serviço HTTP Node no K3s. Traefik encaminha `POST /auth/token` para auth; o Spring atende a API de negócio e valida o token e a propriedade da OS. Os handlers Lambda e o authorizer AWS continuam no repositório para a arquitetura anterior; o serviço OCI não é serverless.

### Contrato e autorização

- Entrada CPF válido de cliente ACTIVE; inválido retorna 400, inexistente/BLOCKED recebem o mesmo 401.
- JWT de 900 segundos, HS256 com SHA-256 dos bytes UTF-8 exatos do segredo compartilhado; `iss=oficina`, `aud=oficina-api`, `scope=CUSTOMER`, `env=hml|prod`, `sub=UUID`, `iat` e `exp`.
- CPF não é incluído no JWT. Spring verifica assinatura/claims, escopo e UUID proprietário. Outro cliente recebe 404 para OS alheia; cliente não acessa funções administrativas.
- Segredos por ambiente e validação de `env` impedem reutilização de token hml em prod e vice-versa. Staff mantém login Spring com senhas externas e escopos próprios.
- No navegador, token de cliente fica em memória e separado da sessão staff.

### Justificativa e limites

Reutilizar o caso de uso e a assinatura existente evita divergência entre Node e Spring e preserva os testes de contrato. CPF é identificador público, não prova de identidade. A alternativa OTP/JWKS da proposta inicial não foi implementada; para uso real é necessária prova de posse, recuperação e revisão do modelo de autenticação. Não se descreve o fluxo acadêmico como autenticação forte.

O adaptador usa pool pequeno e timeouts de consulta, limita corpo a 4 KiB e requisições a 60/minuto por réplica. Esse limite não é rate limit global. Logs carregam identificação de requisição, ambiente, status e duração, sem CPF ou token. `GET /health` verifica o processo, não a disponibilidade do banco.

### Evidências

Autenticação real contra Neon, autorização por proprietário, bloqueio de rotas administrativas e rejeição cruzada hml/prod foram testados. O ensaio de observabilidade enviou 40 entradas inválidas e 40 CPFs sintéticos válidos inexistentes em hml, produzindo respostas 400/401 e dois incidentes New Relic. Esses alertas não comprovam falhas 5xx ou entrega de notificações externas. [Links da entrega e vídeo](https://github.com/DevEgF/soat-oficina-app/blob/develop/README.md).

## Histórico: autenticação Lambda da Fase 3

Status: Accepted - AWS Lambda com entrada exclusivamente CPF.

`POST /auth/token` consulta um cliente ACTIVE no schema do ambiente e emite JWT de 900 segundos: `iss=oficina`, `aud=oficina-api`, `scope=CUSTOMER`, `env=hml|prod`, `sub=UUID`, `iat` e `exp`. HS256 usa SHA-256 dos bytes UTF-8 exatos do segredo compartilhado, garantindo compatibilidade entre Node e Spring.

O authorizer Lambda e o decoder Spring validam o token; a aplicação verifica a propriedade da OS pelo UUID. O CPF não segue na identidade do token nem nas chamadas de acompanhamento/decisão. CPF inexistente e cliente BLOCKED recebem o mesmo 401. O frontend mantém o Bearer em memória sem misturá-lo ao login staff.

CPF é identificador público, não segredo nem segundo fator. Esta autenticação atende à restrição acadêmica e não comprova posse de identidade para um produto público. Uma evolução real precisa de prova de posse e mecanismos de recuperação próprios. A API administrativa pode continuar cadastrando CNPJ; a autenticação do cliente aceita somente CPF.

Decisão anterior: validação customizada por JWKS no GCP. Status: Superseded. Staff continua com login Spring e escopos separados; as cinco senhas implantadas são externas e sem valores padrão.
