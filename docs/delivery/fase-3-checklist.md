# Checklist de entrega da Fase 3

Estado após a demonstração de 11/09/2026: implantação e validação técnica na AWS concluídas; conta encerrada pelo responsável para evitar custos recorrentes. Workflows AWS desabilitados, CI ativo. As afirmações anteriores de entrega exclusivamente local foram superadas por esta execução.

| Item | Situação e evidência |
| --- | --- |
| Quatro repositórios públicos | Confirmados; cada um tem o registro de encerramento abaixo |
| Acesso do avaliador | soat-architecture com permissão write confirmado nos quatro; sem convite pendente |
| PRs e proteção de main | Regras de PR e checks confirmadas; promoção da aplicação por PR #6 |
| Deploy hml/prod | Executado com sucesso; evidência histórica, ambiente atualmente encerrado |
| Autenticação e autorização | CPF/JWT, proprietário e isolamento entre ambientes passaram |
| API e jornada de OS | Postman 12 requisições/19 verificações por ambiente; jornada até DELIVERED |
| Imagem e rollback | Mesmo digest hml/prod; rollback e restauração preservaram dados |
| Observabilidade | Logs/métricas/traces reais coletados; canários passaram; métrica de Finalização e painel diário continuam pendentes |
| Alerta de falha de OS | Configurado; disparo/entrega não demonstrados |
| Documentação arquitetural | Disponível, com ajustes identificados no aceite |
| Vídeo | Arquivo de 8min14s, 1080p, sem áudio preservado; publicação YouTube/Vimeo não comprovada |
| Conteúdo do vídeo | APIs reais e capturas de evidências; demonstração de pipeline/deploy e navegação ao vivo não estão completas |
| PDF do portal e submissão | Não produzidos/comprovados por esta execução |
| Encerramento | Aplicações removidas; conta encerrada conforme responsável; exclusão individual de toda a infraestrutura não confirmada |

## Registros publicados

- [Aplicação](encerramento-aws.md).
- [Autenticação](https://github.com/DevEgF/soat-oficina-auth/blob/main/docs/delivery/encerramento-aws.md).
- [Fundação EKS](https://github.com/DevEgF/soat-oficina-infra-k8s/blob/main/docs/delivery/encerramento-aws.md).
- [Banco RDS](https://github.com/DevEgF/soat-oficina-infra-db/blob/main/docs/delivery/encerramento-aws.md).

Veja também o [aceite detalhado](../acceptance.md) e o [índice da gravação](video-script.md). Não usar links históricos de execução como promessa de endpoint atualmente ativo e não reativar automaticamente os workflows AWS.
