# RFC 0001 - Nuvem

Status: Accepted - AWS, revisão da Fase 3 em 2026-09-09.

A decisão vigente usa API Gateway HTTP API, Lambda, EKS, ECR, RDS PostgreSQL, Secrets Manager e CloudWatch. Terraform divide a entrega em fundação, banco e autenticação; Helm entrega a aplicação. A familiaridade com AWS e a integração entre IAM, Pod Identity e observabilidade sustentam a escolha.

Decisão anterior: GCP, GKE e Cloud SQL. Status: Superseded. A proposta comparativa anterior não governa a implementação desta fase.

Esta arquitetura gera cobrança enquanto provisionada. O orçamento acadêmico exige execução em janelas curtas e acompanhamento dos custos; créditos promocionais não equivalem a serviços gratuitos. Consulte a estimativa e os procedimentos de destruição do repositório de fundação. A subida ocorre somente depois de toda implementação e validação local.
