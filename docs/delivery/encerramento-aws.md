# Registro de execução e encerramento AWS - Aplicação

## Por que o serviço AWS foi encerrado

O ambiente foi criado temporariamente para integração, validação e gravação da Fase 3. Após concluir a demonstração e preservar o vídeo e as evidências, o responsável encerrou a conta AWS para evitar custos recorrentes de manter a infraestrutura ligada.

A limpeza começou somente depois da verificação do vídeo. Durante esse processo, o acesso à AWS foi bloqueado e a tela de login informou suspensão; posteriormente, o responsável confirmou que encerrou a conta. O encerramento foi informado pelo titular. Não foi possível consultar independentemente o inventário final, e este documento não afirma que todos os recursos foram individualmente destruídos ou que o saldo final foi auditado.

## Estado atual de CI/CD e acesso

- Os workflows que acessam a AWS foram desabilitados manualmente no GitHub nos quatro repositórios; os arquivos permanecem versionados como documentação executável da entrega.
- Os workflows de CI permanecem ativos para testes e validações sem deploy AWS. Nenhum workflow de nuvem deve ser reativado automaticamente por esta documentação.
- Os links de Actions abaixo são evidências históricas de execução, não endpoints ativos. O ambiente AWS foi encerrado após a demonstração e não é oferecido para acesso atual do avaliador.
- Os quatro repositórios são públicos. O acesso de escrita do usuário `soat-architecture` foi reconfirmado nos quatro, sem convite pendente.
- Código, documentação e histórico de PRs foram preservados. Não é necessário reabrir a conta apenas para ler os repositórios ou assistir ao vídeo preservado.

Uma futura implantação dependerá de decisão explícita do responsável, conta acessível, revisão de custos/permissões e novos planos Terraform. Não reaplicar planos históricos nem presumir recursos ainda existentes.

## O que foi executado

- Aplicação Kotlin/Spring Boot implantada no Amazon EKS em homologação e produção, com Helm, probes, HPA e migração Flyway anterior ao runtime.
- GitHub Actions validou backend, imagem Docker, segurança e instalação Helm. Produção promoveu a mesma imagem aprovada em homologação, sem reconstrução do artefato promovido.
- Autenticação CPF/JWT, autorização por proprietário e rejeição de token entre ambientes foram verificadas nas APIs reais.
- A coleção Postman executou 12 requisições e 19 verificações aprovadas em cada ambiente. A jornada de OS foi executada até DELIVERED.
- Rollback controlado de homologação para a revisão anterior e restauração da revisão final preservaram a mesma ordem entregue.
- Foram coletados gráficos reais do CloudWatch, registros de correlação e resumos de traces do X-Ray. Os canários mais recentes passaram nos dois ambientes antes da limpeza.

Imagem promovida: `sha256:3a9cecdd178e0ae6e6804f0b1842af04085800f660ef16acfee89df2653e45c0`.

### Evidências públicas

- [Deploy homologação](https://github.com/DevEgF/soat-oficina-app/actions/runs/34664972364).
- [Deploy produção](https://github.com/DevEgF/soat-oficina-app/actions/runs/34665628443).
- [PR de promoção para produção](https://github.com/DevEgF/soat-oficina-app/pull/6).
- [Remoção da aplicação hml concluída](https://github.com/DevEgF/soat-oficina-app/actions/runs/34666637573).
- [Remoção da aplicação prod concluída](https://github.com/DevEgF/soat-oficina-app/actions/runs/34666638595).

### Gravação e limites do aceite

Foi gerado e preservado pelo responsável um vídeo de 8min14s, 1920 × 1080, sem áudio, com chamadas reais das APIs e evidências AWS. O arquivo passou por decodificação integral e revisão de quadros. SHA256: `c857194957c37465af6ee210f42f592d1ebc5aab37b597a553e88d3cf437b12a`.

A publicação do vídeo no YouTube/Vimeo e o PDF único do portal ainda não estão comprovados neste registro. O vídeo mostra resultados de pipeline e capturas de observabilidade; não mostra a execução do GitHub Actions ou a navegação no dashboard ao vivo. Não deve ser apresentado como aceite integral de todos os requisitos formais.

O dashboard instrumenta DIAGNOSIS, APPROVAL e EXECUTION; a métrica de Finalização exigida pelo enunciado continua pendente. O volume de OS usa buckets de 60 segundos, sem painel explicitamente diário. O alarme de falhas técnicas foi configurado, mas não houve evidência de disparo e entrega de notificação de falha de OS.

## Registros dos quatro componentes

- [Aplicação](https://github.com/DevEgF/soat-oficina-app/blob/main/docs/delivery/encerramento-aws.md)
- [Autenticação](https://github.com/DevEgF/soat-oficina-auth/blob/main/docs/delivery/encerramento-aws.md)
- [Fundação EKS](https://github.com/DevEgF/soat-oficina-infra-k8s/blob/main/docs/delivery/encerramento-aws.md)
- [Banco RDS](https://github.com/DevEgF/soat-oficina-infra-db/blob/main/docs/delivery/encerramento-aws.md)

As datas dos workflows podem aparecer em 12/09/2026 UTC; a demonstração foi gravada em 11/09/2026 no horário de Brasília.
