# ADR 0001 - Ambientes compartilhados

Status: Accepted.

Para limitar os custos da demonstração, hml e prod compartilham cluster EKS, VPC, NLB, ECR, uma instância RDS e o segredo de assinatura JWT. O isolamento lógico usa namespaces, schemas, listeners, Gateway por ambiente, roles OIDC, segredo staff por ambiente e claim `env` validada em duas camadas.

Essa opção não fornece isolamento físico nem a independência operacional exigida por sistemas críticos. A credencial RDS compartilhada alcança os dois schemas; autorização na aplicação e configuração do schema delimitam o uso. O segredo JWT compartilhado permite assinatura para ambos os ambientes a quem o obtiver. Esses limites são assumidos para a entrega acadêmica.

Namespaces e ServiceAccounts são bootstrap da fundação. Helm cuida das revisões da aplicação e das migrações, retendo dependências para rollback. A ordem inicial é estado remoto, fundação, RDS, bootstrap Kubernetes e provisionamento IaC dos recursos de autenticação/Gateway. Publique a URL obtida em `API_BASE_URL`, implante aplicação/migrações/fixtures e execute os pipelines completos de autenticação. Essa separação inicial resolve a dependência entre a URL exigida pelo deploy da aplicação e as tabelas exigidas pelo smoke de autenticação. Provisionar recursos não significa passar no aceite; a validação externa completa é obrigatória após todos os componentes estarem disponíveis.
