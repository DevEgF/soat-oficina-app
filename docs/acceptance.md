# Aceite da Fase 3

A implantação AWS foi executada em homologação e produção, validada e gravada em 11/09/2026 (Brasília). Depois da preservação do vídeo, o responsável encerrou a conta para evitar custos recorrentes. Os workflows AWS foram desabilitados; CI permanece ativo.

O [registro de execução e encerramento](delivery/encerramento-aws.md) reúne os links dos workflows e os limites da limpeza. O ambiente não é anunciado como disponível atualmente.

## Evidência de execução concluída

- Deploys da aplicação, Lambda/Gateway, fundação EKS e RDS concluídos.
- Mesma imagem por digest em hml/prod; promoção de produção sem reconstruir o artefato aprovado.
- Autenticação CPF/JWT, bloqueio de clientes inválidos/bloqueados/inexistentes, propriedade da OS e rejeição de token entre ambientes verificados nas APIs reais.
- Postman: 12 requisições e 19 verificações aprovadas em cada ambiente.
- Jornada completa de ordem de serviço executada até DELIVERED.
- Rollback controlado em hml e restauração da versão final preservaram a mesma ordem entregue.
- Logs correlacionados, métricas reais e resumos X-Ray coletados; canários de saúde passaram nos dois ambientes.
- Vídeo local de 8min14s, 1080p, sem áudio, com decodificação integral aprovada e revisão de quadros.

## Limites do aceite integral

O resultado técnico não substitui os entregáveis formais do enunciado. Continuam pendentes de conclusão/comprovação:

- Métrica de duração da etapa Finalização e painel explicitamente diário de volume de OS; a instrumentação atual usa DIAGNOSIS/APPROVAL/EXECUTION.
- Disparo e entrega de um alerta de falha técnica de OS; configurar o alarme não comprova sua notificação.
- Atualização da sequência da OS/ER e inclusão dos diagramas e links de API exigidos em cada repositório.
- Demonstração visual da execução do pipeline/deploy e análise de dashboard/logs/traces ao vivo. O vídeo atual usa chamadas reais de API, capturas CloudWatch e resumos de evidências históricas.
- Publicação do vídeo no YouTube/Vimeo e PDF único com os links para submissão no Portal do Aluno.

A remoção das aplicações foi concluída. A destruição da autenticação foi parcial, e o inventário restante não pôde ser conferido após a perda de acesso à conta. Esse limite operacional está detalhado no registro de encerramento.

## Histórico de testes locais

Antes da implantação, em 09/09/2026, foram registrados 156 testes de backend, um ignorado, cobertura JaCoCo de linhas de 89,93%, quatro testes frontend, build/lint, migrações Flyway, Helm/kind e scan Trivy sem HIGH/CRITICAL naquele artefato. Esses números são históricos; não representam uma nova contagem do último commit.

Os links dos workflows finais são a referência para os gates da versão implantada. Use apenas dados sintéticos no Postman e não exporte senhas, tokens ou estados Terraform.
