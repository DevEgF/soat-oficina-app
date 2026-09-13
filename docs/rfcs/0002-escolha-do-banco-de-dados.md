# RFC 0002 - Banco gerenciado

## Decisão atual: PostgreSQL 16 gerenciado no Neon

Status: Accepted - Neon para a implantação OCI, atualização de 13/09/2026.

Mantemos a engine PostgreSQL e mudamos o serviço de RDS para Neon após o encerramento da conta AWS. Clientes, veículos, ordens de serviço, catálogo, peças e reservas formam um modelo relacional. Transações, constraints, chaves estrangeiras e índices únicos são necessários para consistência de negócio. JPA/Hibernate e as migrations Flyway existentes continuam sendo a base da persistência.

### Alternativas

| Alternativa | Decisão |
|---|---|
| PostgreSQL no RDS | Implementado e preservado em Terraform; indisponível como destino atual após encerramento AWS |
| PostgreSQL dentro da VM/K3s | Evitaria provedor externo, mas dividiria RAM/disco com app e exigiria operar banco, backup e recuperação no único nó |
| Neon PostgreSQL | Escolhido para manter engine/migrations e retirar operação do banco da VM de 12 GB |
| MySQL | Mudança de DDL e semântica temporal sem benefício funcional demonstrado |
| SQL Server | Novo dialeto/driver e considerações de licença sem necessidade do domínio |
| Banco documental | Remodelagem de relações e consistência sem caso de uso que justifique a mudança |

### Configuração e segurança

Projeto Neon `oficina` em Ohio (`us-east-2`), PostgreSQL 16, database `neondb`, schemas `hml` e `prod`. Usar conexão direta sem pooler para o caminho de migração validado; TLS verify-full com CA confiável. Sete migrations foram aplicadas em cada schema. Flyway roda no Job anterior ao rollout; Hibernate valida o schema. `hibernate.default_schema` é informado preservando o underscore da propriedade via SPRING_APPLICATION_JSON.

O usuário proprietário é compartilhado pelos dois schemas. Há separação lógica de dados, não isolamento de privilégios ou de infraestrutura. Uma evolução precisa de papéis/grants por ambiente. Senhas são configuradas por entrada protegida, persistidas em arquivos root-only e Kubernetes Secrets; não devem aparecer em documentação, values, saídas ou Git.

### Trade-offs e recuperação

A VM OCI fica em Ashburn e o banco em Ohio, com conexão entre provedores pela internet. Há dependência de conectividade, limites de plano e orçamento de conexões. Não se afirma SLA, retenção de backup ou restauração ensaiada sem verificar o plano e executar o procedimento. Helm rollback não desfaz migrations; manter alterações aditivas e planejar backup/restauração para alterações destrutivas.

O projeto Neon foi criado pelo console; **não foi provisionado pelo módulo Terraform RDS**. Esse módulo continua sendo a entrega IaC histórica do banco AWS. Credenciais S3/AI Gateway não são credenciais PostgreSQL. [Operação, justificativas e evidências](../../README.md).

## Histórico: RDS PostgreSQL da Fase 3

Status: Accepted - AWS RDS for PostgreSQL 16.

RDS preserva transações e integridade do modelo relacional de OS, clientes, veículos, catálogo, estoque e reservas. JPA valida o schema; Flyway executa exclusivamente no Job de migração antes do rollout. A conexão usa TLS verify-full com a cadeia de confiança RDS empacotada na imagem.

Uma instância compartilhada atende hml/prod em schemas separados. Isso reduz o custo acadêmico, mas não equivale a bancos, credenciais ou infraestrutura fisicamente isolados. A credencial gerenciada do RDS é resolvida em runtime pelo CSI/Pod Identity e pelas Lambdas; Terraform publica somente seu ARN e o ARN KMS correspondente.

Decisão anterior: Cloud SQL for PostgreSQL. Status: Superseded. PostgreSQL local em kind permanece apenas como ambiente de teste. Migrações precisam ser compatíveis com a revisão anterior: rollback Helm não desfaz migrações já aplicadas.
