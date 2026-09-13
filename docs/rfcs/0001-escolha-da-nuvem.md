# RFC 0001 - Nuvem

## Decisão atual: continuação OCI/K3s

Status: Accepted - OCI/K3s para continuidade operacional, atualização de 13/09/2026.

A conta AWS foi encerrada pelo responsável após a demonstração para evitar custos recorrentes. Manter EKS, workers, RDS e componentes de rede consumia o orçamento acadêmico mesmo com baixa carga; créditos e budgets não tornam esses recursos permanentemente gratuitos. A migração não decorreu de incapacidade funcional da AWS.

A VM OCI disponível em Ashburn (`us-ashburn-1`) executa Oracle Linux 9.8 ARM64, com 2 OCPUs e 12 GB de memória. K3s de nó único hospeda API Spring e autenticação HTTP em namespaces hml/prod. Traefik recebe HTTPS nos hosts baseados no IP via sslip.io; cert-manager e Let's Encrypt gerenciam certificados. Neon PostgreSQL em Ohio mantém a persistência fora da VM; New Relic recebe logs, APM, traces, métricas Kubernetes e agregados de negócio.

### Alternativas e justificativa

| Alternativa | Avaliação |
|---|---|
| Manter AWS permanentemente | Maior continuidade com a arquitetura inicial, mas incompatível com a conta encerrada e o objetivo de reduzir despesa recorrente |
| Recomeçar em GCP | Proposta anterior; exigiria nova configuração de conta, IAM, rede e serviços e manteria dependência de créditos |
| OCI gerenciado com OKE/Functions | Possível evolução; não foi o caminho implantado e não deve ser apresentado como evidência executada |
| VM OCI + K3s + Neon | Escolhida para aproveitar capacidade já disponível e reaproveitar contratos, containers e migrations |
| Apenas ambiente local | Reproduzível para desenvolvimento, mas não mantém a API pública disponível |

### Consequências e limites

- A equipe assume manutenção do sistema, Kubernetes, armazenamento local e recuperação. Um nó não oferece alta disponibilidade entre nós; HPA de pods não cria capacidade física adicional.
- O adaptador de autenticação é HTTP em container, não função serverless. K3s não é EKS/OKE gerenciado; a evidência AWS preservada deve ser usada para requisitos específicos desses serviços.
- O banco cruza região e provedor por TLS. Custos, franquias, latência e disponibilidade precisam ser acompanhados; não há compromisso de custo zero permanente.
- Imagens ARM64 foram importadas no containerd e promovidas por digest. Registry remoto e pipeline OCI completos não foram executados.
- Segredos locais protegidos e Kubernetes Secrets foram autorizados para OCI. Não reativar os workflows AWS nem migrar valores para Git.

### Validação registrada

HTTPS HML/PROD, sete migrations por schema, saúde/login/leitura em produção, jornada de OS em hml, rejeição de JWT cruzado, dashboards e dois incidentes de autenticação foram verificados. Rollback OCI validou configuração Helm e preservação de OS com os mesmos digests. [Links e matriz operacional](../../README.md).

## Histórico: decisão AWS da Fase 3

O registro abaixo descreve a arquitetura anterior, preservada no código. A expressão "decisão vigente" refere-se à revisão de 09/09/2026, não à operação OCI atual.

Status: Accepted - AWS, revisão da Fase 3 em 2026-09-09.

A decisão vigente usa API Gateway HTTP API, Lambda, EKS, ECR, RDS PostgreSQL, Secrets Manager e CloudWatch. Terraform divide a entrega em fundação, banco e autenticação; Helm entrega a aplicação. A familiaridade com AWS e a integração entre IAM, Pod Identity e observabilidade sustentam a escolha.

Decisão anterior: GCP, GKE e Cloud SQL. Status: Superseded. A proposta comparativa anterior não governa a implementação desta fase.

Esta arquitetura gera cobrança enquanto provisionada. O orçamento acadêmico exige execução em janelas curtas e acompanhamento dos custos; créditos promocionais não equivalem a serviços gratuitos. Consulte a estimativa e os procedimentos de destruição do repositório de fundação. A subida ocorre somente depois de toda implementação e validação local.
