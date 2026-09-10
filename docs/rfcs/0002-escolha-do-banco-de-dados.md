# RFC 0002 - Banco gerenciado

Status: Accepted - AWS RDS for PostgreSQL 16.

RDS preserva transações e integridade do modelo relacional de OS, clientes, veículos, catálogo, estoque e reservas. JPA valida o schema; Flyway executa exclusivamente no Job de migração antes do rollout. A conexão usa TLS verify-full com a cadeia de confiança RDS empacotada na imagem.

Uma instância compartilhada atende hml/prod em schemas separados. Isso reduz o custo acadêmico, mas não equivale a bancos, credenciais ou infraestrutura fisicamente isolados. A credencial gerenciada do RDS é resolvida em runtime pelo CSI/Pod Identity e pelas Lambdas; Terraform publica somente seu ARN e o ARN KMS correspondente.

Decisão anterior: Cloud SQL for PostgreSQL. Status: Superseded. PostgreSQL local em kind permanece apenas como ambiente de teste. Migrações precisam ser compatíveis com a revisão anterior: rollback Helm não desfaz migrações já aplicadas.
