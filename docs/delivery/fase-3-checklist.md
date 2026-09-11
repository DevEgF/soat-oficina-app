# Checklist de entrega da Fase 3

Atualizado em 11/09/2026. O usuário autorizou a publicação dos commits e,
posteriormente, a conclusão incluindo deploy. A implantação deve usar planos
novos e seguir a ordem dos runbooks: bootstrap, fundação, banco, recursos de
autenticação, aplicação e validação integrada. A autorização não comprova
execução: os resultados em nuvem serão registrados após os respectivos gates.

## Implementação e evidências

As evidências históricas abaixo estão detalhadas em [aceite](../acceptance.md)
e no handoff de 09/09 do workspace de coordenação. Não representam uma nova
execução em AWS nem comprovam endpoints ativos.

| Requisito | Implementação / evidência | Situação |
|---|---|---|
| Aplicação e autorização por proprietário | Backend, frontend e testes; 156 testes de backend, um ignorado; quatro testes frontend | Implementado; gates locais registrados |
| Autenticação CPF | Lambda, JWT de 900 segundos, status ACTIVE/BLOCKED e testes no repositório auth | Implementado; 79 testes registrados |
| Banco de dados | Terraform RDS, sete migrações Flyway, schemas hml/prod | Implementado; quatro testes Terraform e migração local registrados |
| Kubernetes | Chart Helm, probes, HPA, Job Flyway e isolamento por namespace | Instalação, atualização e rollback kind registrados |
| Infraestrutura | Terraform EKS, rede, IAM, observabilidade e orçamento | Sete testes Terraform e validação local registrados |
| CI/CD | Checks de PR, promoção por digest e recuperação de release | Quatro PRs integrados; execução de deploy não comprovada |
| Segurança da imagem | Docker e Trivy; Tomcat 11.0.25 | Zero HIGH/CRITICAL no scan registrado em 09/09 |
| Logs e métricas | JSON, EMF e teste de transporte Fluent Bit | Transporte local validado; visualização CloudWatch não validada |
| Traces | Instrumentação da aplicação e autenticação | Trace completo em nuvem não validado |
| API | Swagger e coleção Postman Fase3 | Contratos locais disponíveis; jornada Gateway completa não validada |
| Arquitetura | Diagramas de componentes, autenticação, OS e RFCs | Documentada em docs/architecture e docs/rfcs |
| Vídeo | [Roteiro de 12 minutos](video-script.md) | Roteiro pronto; gravação e revisão humanas ainda necessárias |
| Acesso do avaliador | Colaborador soat-architecture previsto na configuração GitHub | Aceite do convite não reconfirmado nesta sessão |
| Custos e encerramento | Infraestrutura anterior destruída conforme handoff | Sem consulta de custo ou inventário AWS nesta sessão |

## Repositórios e integração registrada

| Repositório | PR integrado |
|---|---|
| [Aplicação](https://github.com/DevEgF/soat-oficina-app) | [PR 4](https://github.com/DevEgF/soat-oficina-app/pull/4) |
| [Autenticação](https://github.com/DevEgF/soat-oficina-auth) | [PR 1](https://github.com/DevEgF/soat-oficina-auth/pull/1) |
| [Fundação Kubernetes](https://github.com/DevEgF/soat-oficina-infra-k8s) | [PR 7](https://github.com/DevEgF/soat-oficina-infra-k8s/pull/7) |
| [Banco](https://github.com/DevEgF/soat-oficina-infra-db) | [PR 3](https://github.com/DevEgF/soat-oficina-infra-db/pull/3) |

Os estados de integração são os registrados em 10/09; não houve nova consulta
remota para este checklist.

## Limite do aceite

O fechamento local não equivale ao aceite integral da entrega em nuvem.
Homologação e produção ativas, promoção ECR, rollback AWS, jornada Postman
através do Gateway, dashboards, traces e vídeo final exigem evidências próprias.
Essas atividades estão autorizadas, mas ainda não têm conclusão registrada.
Planos Terraform e metadados de 08/09 são históricos e não devem ser reutilizados
como plano de implantação ou prova de disponibilidade atual.
