# Aceite da Fase 3

Estado em 2026-09-09: implementação revisada e gates locais executados. Nenhum endpoint AWS está apresentado como ativo. A infraestrutura anterior foi destruída; planos Terraform antigos não devem ser reaplicados.

## Evidência local disponível

- Backend final: 156 testes, zero falhas/erros, um ignorado; JaCoCo 89,93% de linhas e 82,76% de instruções. Build Docker executou novamente `check bootJar` com sucesso após o patch de Tomcat 11.0.25.
- Frontend: lint, quatro testes HTTP e build passaram; auditoria npm sem vulnerabilidades naquele lockfile.
- Processo real de migração: sete versões aplicadas em schema local isolado, sem servidor web, saída zero.
- Helm: manifests hml/prod/local e digest inválido verificados semanticamente; lint passou.
- Recuperação de deploy: seis cenários simulados passaram, sem rede/AWS/Kubernetes.
- Imagem completa: Trivy 0.69.3 fixado por digest, zero HIGH/CRITICAL após atualização de Tomcat 11.0.24 para 11.0.25. Manifest local `sha256:9baa2e2cd3e9ac13a4f597baae8e25a1b1021cca18b3cc4c6d5b312a212fec87`; o digest ECR será registrado na entrega em nuvem.
- Kubernetes local: instalação e atualização Helm concluídas, sete versões Flyway aplicadas, fixtures repetidas sem duplicação e healthcheck HTTP `UP`.
- Rollback local: retorno à configuração `oficina-hml-r1` concluído, healthcheck `UP`, sete migrações, três clientes e três ordens preservados. A promoção/recuperação por digest ECR continua pendente de validação em nuvem.
- HPA: CPU 70% e memória 80% verificados nos manifests dos dois ambientes.
- Transporte de métricas: Fluent Bit oficial enviou PutLogEvents a receptor HTTPS local preservando EMF na raiz, correlação e CRI fragmentado; roteamento hml/prod e exclusão de outros namespaces passaram. O teste está no CI da foundation.

Verificações em nuvem ainda pendentes:

- CPF ACTIVE obtém token de 900s; BLOCKED/inexistente têm o mesmo 401.
- Bearer ausente recebe 401; escopo staff e replay de outro ambiente recebem 403 no Gateway.
- Propriedade por UUID: dono consulta/decide sua OS, outro cliente recebe 404.
- Históricos Flyway hml/prod separados; Deployment com migração desabilitada.
- HPA, probes, logs JSON, traces e métricas de criação/duração/falha visíveis.
- Alteração do canário mantendo engine ARN reinicia a execução após o apply.
- Rollback retorna ao digest anterior e preserva o banco.
- Links dos workflows, imagens/digests, dashboards e evidências do vídeo registrados sem CPF, tokens ou senhas reais.

Use clientes e ordens sintéticas distintas para aprovação e rejeição no Postman. Preencha valores apenas durante a execução; não exporte credenciais nem tokens. As variáveis de token da coleção são locais ao run e limpas no último request.
