# Oficina - Fase 3

> **Ambiente AWS encerrado após a demonstração para evitar custos recorrentes.** A implantação e os testes foram executados; os workflows AWS estão desabilitados e o CI permanece ativo. Consulte o [registro de execução, evidências e limites da remoção](docs/delivery/encerramento-aws.md). Não há endpoint AWS ativo anunciado.

Aplicação Kotlin/Spring Boot com PostgreSQL, frontend React e entrega na AWS por quatro repositórios: aplicação, autenticação Lambda, fundação EKS e banco RDS. O código desta fase substitui as rotas públicas de acompanhamento por autenticação de cliente com CPF e JWT vinculado ao UUID do cliente.

## Execução local

Java 17 e PostgreSQL 16 são necessários. O perfil `local` habilita exclusivamente credenciais sintéticas de desenvolvimento:

```powershell
$env:SPRING_PROFILES_ACTIVE='local'
./oficina/gradlew -p oficina bootRun
```

Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` para seu banco local. O frontend usa `npm ci` e `npm run dev` em `frontend/`; veja [a configuração dos dois clientes HTTP](frontend/README.md). A Lambda de autenticação fica em `soat-oficina-auth`; não existe `/auth/token` no processo Spring local.

Credenciais padrão são permitidas apenas quando todos os perfis ativos são `local` ou `test`. O ambiente implantado usa o perfil `docker`, exige JWT e as cinco senhas staff externas e rejeita padrões conhecidos, inclusive em combinações `docker,test`. Os usuários são `master`, `admin`, `atendente`, `tecnico` e `almoxarife`; senhas de AWS nunca ficam no repositório.

## API

- `POST /auth/token` no API Gateway: corpo `{ "cpf": "..." }`, cliente ACTIVE, JWT de 900 segundos. BLOCKED e CPF inexistente têm a mesma resposta 401; CPF inválido retorna 400.
- `GET /api/customer/os/acompanhar?codigo=...`: Bearer CUSTOMER; propriedade validada pelo UUID de `sub`.
- `POST /api/customer/os/aprovar-orcamento?codigo=...` e `/reprovar-orcamento?codigo=...`: mesmo Bearer e propriedade.
- `POST /api/customer/os/orcamento/decisao`: `{ "codigo": "...", "decisao": "APROVADO" | "RECUSADO" }` com Bearer CUSTOMER.
- `POST /api/public/auth/login`: login staff. `/api/admin/**`, `/api/attendant/**`, `/api/technician/**` e `/api/warehouse/**` exigem o escopo correspondente.
- `/actuator/health`, `/actuator/health/readiness` e `/actuator/health/liveness`: probes; documentação local em `/swagger-ui.html` e `/v3/api-docs`.

CPF só é credencial de entrada da Lambda; não é prova forte de identidade e não deve ser tratado como solução de autenticação para um produto público. A limitação acadêmica está registrada na [RFC de autenticação](docs/rfcs/0003-estrategia-de-autenticacao.md). Tokens levam `iss=oficina`, `aud=oficina-api`, `env=hml|prod`, `scope=CUSTOMER`, `sub=UUID`, `iat` e `exp`; assinatura HS256 usa SHA-256 dos bytes UTF-8 exatos da chave compartilhada.

## Entrega e validação

Os checks obrigatórios são `app / backend`, `app / image`, `app / helm-smoke` e `app / security`. Executam backend/JaCoCo, frontend lint/test/build, Docker, Trivy, validação Helm e instalação kind. `develop` publica a imagem testada em ECR e implanta hml. `main` promove exatamente o digest atestado por uma implantação hml bem-sucedida; a entrega prod não reconstrói nem publica imagem.

O [chart Helm](deploy/helm/oficina/README.md) executa Flyway em Job antes do runtime, usa TLS verify-full e configurações por revisão. Falhas de smoke externo provocam rollback explícito. Migrações devem continuar compatíveis com a versão anterior; rollback não desfaz dados. Remoção e rollback são workflows manuais, separados por ambiente; remoção exige `DESTROY-soat-oficina-app` e preserva cluster, namespaces e banco.

```powershell
./oficina/gradlew -p oficina --no-daemon clean check bootJar
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
python deploy/helm/oficina/tests/render.py
pwsh -File scripts/test-workflows.ps1
pwsh -File scripts/test-delivery.ps1
pwsh -File scripts/test-evidence.ps1
```

Os scripts Python de verificação usam PyYAML. Testes de integração usam somente banco PostgreSQL descartável. O build Docker também executa os testes e precisa alcançar esse banco através de `BUILD_TEST_DATASOURCE_URL`.

## Documentação

- [Componentes](docs/architecture/componentes.md), [autenticação](docs/architecture/sequencia-auth.md) e [jornada da OS](docs/architecture/sequencia-os.md).
- [Contratos entre repositórios](docs/architecture/integration-contracts.md).
- [Ambientes compartilhados](docs/adrs/0001-ambientes-compartilhados.md).
- [Coleção Postman](postman/Fase3.postman_collection.json) e [ambiente sem credenciais](postman/hml.postman_environment.json).
- [Roteiro e evidências de aceite](docs/acceptance.md).
- [Checklist consolidado da entrega](docs/delivery/fase-3-checklist.md) e [roteiro do vídeo](docs/delivery/video-script.md).

A implantação em nuvem foi concluída e validada antes do encerramento do ambiente. Os links históricos de execução e as pendências formais da entrega estão no [registro de encerramento](docs/delivery/encerramento-aws.md).
