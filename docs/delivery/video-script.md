# Roteiro do vídeo da Fase 3

Duração prevista: 12 minutos. Versão local de 11/09/2026, sem implantação AWS.
Apresentar somente código e evidências existentes. Dizer no início que a
arquitetura AWS está implementada, mas a nuvem não será ativada nesta demonstração.
Não abrir arquivos de ambiente, estados Terraform ou dados de clientes reais.

| Tempo | Conteúdo e material |
|---|---|
| 00:00-00:45 | Explicar o desafio da oficina e mostrar os quatro repositórios listados no checklist. Informar o escopo local da demonstração. |
| 00:45-02:00 | Mostrar docs/architecture/componentes.md e o isolamento por namespace, schema, API e claim de ambiente. |
| 02:00-03:15 | Explicar o código Terraform EKS/RDS, rede privada e controles de custo. Mostrar resultados locais registrados, sem executar apply ou abrir console como prova de recursos ativos. |
| 03:15-05:15 | Mostrar o diagrama de autenticação e testes de CPF inválido, ACTIVE, BLOCKED e inexistente; explicar validade do JWT e rejeição de ambiente incorreto. Não exibir tokens. |
| 05:15-07:30 | Mostrar o diagrama da OS e testes de consulta, aprovação, rejeição e acesso de outro cliente. Uma demonstração executável exige ambiente local previamente preparado com dados sintéticos; a Lambda não está embutida no Spring. |
| 07:30-09:00 | Mostrar workflows e checks registrados nos PRs, promoção do mesmo digest e seis cenários de recuperação. Diferenciar rollback kind já registrado de rollback AWS ainda não validado. Não acionar workflows. |
| 09:00-10:30 | Mostrar instrumentação JSON/EMF, correlação e teste de transporte Fluent Bit. Explicar que dashboards e trace ponta a ponta em CloudWatch não foram capturados nesta etapa. |
| 10:30-11:30 | Mostrar coleção Postman, definições OpenAPI e resultados de testes em docs/acceptance.md, identificando a data das evidências. |
| 11:30-12:00 | Mostrar checklist, links públicos e limitações do aceite. Registrar que AWS permaneceu fora da execução e que não há endpoint ativo comprovado. |

## Preparação e revisão

1. Preparar telas de código, diagramas e relatórios locais antes de gravar.
2. Usar 1080p, zoom legível e dados sintéticos; fechar abas com dados pessoais.
3. Não executar scripts de bootstrap, deploy, promoção, remoção ou captura AWS.
4. Gravar a narração distinguindo implementação, testes locais e validação em nuvem.
5. Revisar áudio, legibilidade, duração e ausência de dados sensíveis.
6. Após gravar, registrar o link real e os timestamps no checklist. A gravação
   final ainda não existe; não apresentar este roteiro como vídeo entregue.
