# Continuação OCI, K3s, Neon e New Relic

Implementação adicional integrada em `develop`. Os arquivos AWS existentes não foram
alterados, e nenhum workflow ou recurso AWS foi reativado.

## Estado verificado em 12/09/2026

- Oracle Linux 9.8 ARM64, 2 CPUs, cerca de 10,6 GiB utilizáveis; K3s
  `v1.35.8+k3s1` com nó `oficina-oci` Ready.
- CoreDNS, Traefik e metrics-server operacionais; namespaces hml/prod com Pod
  Security restricted. SELinux Enforcing, firewalld ativo, kubeconfig root:0600
  e criptografia de Secrets habilitada.
- Helm `v3.20.0` instalado na VM. O deploy script usa a opção `--atomic` dessa versão.
- Imagens da aplicação e da autenticação construídas para ARM64 e importadas no
  containerd, inclusive referências por digest descritas em release-values.yaml.
  Elas ainda não foram publicadas em registry; um novo nó exigiria novo import ou registry.
- Job nativo no K3s executou Java 17.0.20 e Node 24.21.0 dessas imagens com sucesso.
- Auth: lint/typecheck e 92 testes passaram, incluindo timeout real de PostgreSQL
  local sintético. Cobertura: linhas 96,87%, branches 91,39%, funções 88%.
- Os ZIPs dos dois handlers Lambda existentes foram reconstruídos e passaram no
  smoke offline, sem operações AWS.
- Chart: lint, testes de rejeição de namespace/digest/TLS e dry-run do servidor
  K3s passaram. Configurador de segredos: três testes passaram em Windows e Linux.
- New Relic `nri-bundle 8.0.24` instalado, cinco pods Ready sem reinícios.
  Chave dedicada `oficina-oci` validada na região US (Metric API HTTP 202).
  Consultas no painel confirmaram K8sNodeSample, Log, Transaction e Span.
- API e auth implantadas em hml/prod com os mesmos digests. Os dois hooks Flyway
  concluíram; conexão Neon direta com TLS verify-full e sete migrações por schema.
- Smoke real: saúde, login staff, leitura no banco, autenticação CPF de cliente
  sintético em hml, rejeição de acesso administrativo por cliente e rejeição de
  tokens entre hml/prod nos dois sentidos. Cliente sintético removido após o teste.
  Posteriormente, a jornada completa até DELIVERED e a autorização entre proprietários foram verificadas em hml; a OS sintética desse ensaio foi preservada.

## Segredos configurados

Secrets oficina-runtime existem em hml/prod e newrelic-license nos três namespaces.
Os valores foram transferidos por SSH, sem entrar no Git ou em saídas de ferramentas.
Arquivos temporários locais de transporte cifrado foram removidos após a importação.
O responsável autorizou substituir Secrets Manager por arquivos root-only e
Kubernetes Secrets para esta implantação. Não enviar valores ao chat/Git.

Para configuração futura, em um terminal SSH interativo:

```bash
sudo python3 /opt/oficina/configure-secrets.py --environment hml
sudo python3 /opt/oficina/configure-secrets.py --environment prod
sudo python3 /opt/oficina/configure-secrets.py --new-relic
```

No Neon, usar **Connect**, desativar **Connection pooling** e copiar a connection
string com SSL. O script gera JDBC com `sslmode=verify-full`, CA do sistema e
schema do ambiente. No New Relic, usar uma chave de ingestão **INGEST - LICENSE**
e informar US/EU. Os prompts de credenciais são ocultos; os arquivos ficam em
`/etc/oficina`, com diretórios 0700 e arquivos 0600. JWT e senhas staff gerados são
preservados nas execuções seguintes. O script não realiza rotação automática.

Schemas distintos separam dados, mas reutilizar um usuário proprietário do Neon
não cria isolamento de privilégios no banco. Para isolamento forte, configurar
papéis dedicados por ambiente e restringir grants antes da entrega final.

## Deploy após configurar o Neon

Os arquivos de deploy ficam em `/opt/oficina/oci`. O pre-install/pre-upgrade Job
executa Flyway usando a mesma imagem da aplicação; uma falha impede o rollout.
Helm pode reverter os pods, mas não desfaz migrações de banco, que devem ser aditivas.

```bash
cd /opt/oficina/oci
sudo bash deploy.sh hml docker.io/library/oficina-oci \
  sha256:a04bdeeb8d553091d6d84b87b29b110e2066afe86ffe6adda3afe7d98d7fff5e \
  -f release-values.yaml
```

Realizar smoke de health, autenticação CPF, proprietário da OS, staff e rejeição
entre ambientes antes da promoção. O script bloqueia produção quando o digest da
aplicação diverge daquele em hml. A imagem auth do release também deve ser preservada.
Use `prod` no mesmo comando somente após o smoke. A primeira promoção privada foi
executada em 12/09/2026; os dois ambientes passaram em `smoke-vm.py`.

```bash
sudo python3 /opt/oficina/oci/smoke-vm.py hml
sudo python3 /opt/oficina/oci/smoke-vm.py prod
```

O smoke usa port-forward somente em loopback, credenciais root-only e remove o
cliente sintético de hml. Produção faz leitura/login e valida rejeição JWT cruzada.

## HTTPS público

HTTPS foi instalado com `install-https.sh`, cert-manager v1.20.3, ClusterIssuer `letsencrypt-production` e desafio HTTP-01 no Traefik. Os dois namespaces possuem Certificate e Secret `oficina-tls`, com renovação automática pelo cert-manager. HTTP redireciona para HTTPS preservando a rota do desafio ACME.

- HML: https://hml.129.213.121.122.sslip.io
- PROD: https://oficina.129.213.121.122.sslip.io

As regras OCI e o firewalld permitem 80/443; SSH já estava disponível. API-server 6443 e kubelet 10250 não precisam de exposição pública. O certificado foi verificado por clientes HTTPS sem desabilitar validação TLS. Mudança do IP exige atualizar hosts e certificados. A renovação depende de DNS, alcance HTTP e disponibilidade do nó.

## Observabilidade

```bash
sudo bash /opt/oficina/oci/install-newrelic.sh
```

O release-values.yaml habilita `observability.newRelic.enabled=true` na região US.
A imagem já inclui Java agent 9.4.0 com checksum verificado;
ele só é ativado quando essa opção está ligada. O chart usa Secret existente.
Fluent Bit coleta logs apenas de hml/prod; encaminhamento duplicado pelo Java é
desativado. Pixie/eBPF e scraper do control plane ficam desativados neste perfil.
Validar métricas de CPU/memória, transações, logs e traces recebidos no New Relic.
No Oracle Linux, Fluent Bit usa contexto SELinux `spc_t` apenas no coletor para
ler logs do host, com capabilities removidas e privilege escalation desabilitado.
SELinux do host permanece Enforcing. Offsets ficam em `/var/lib/oficina-fluentbit`,
rotulado container_file_t pelo instalador; após relabel do host, reaplicar o script.
Hibernate recebe `hibernate.default_schema` via SPRING_APPLICATION_JSON para
preservar o underscore do nome da propriedade e separar hml/prod corretamente.
O coletor `business-telemetry.py`, instalado por `install-business-telemetry.sh`, consulta agregados PostgreSQL a cada cinco minutos via systemd. Usa cliente PostgreSQL 16, TLS verify-full, schema validado e consultas somente leitura com timeout. O cliente 13 originalmente disponível não atendia à conexão Neon deste ambiente.

| Evento | Uso correto |
|---|---|
| `OficinaBusinessDaily` | Criadas, finalizadas e entregues por dia; sete dias no fuso America/Sao_Paulo, incluindo zeros; usar latest() por dia para não somar snapshots repetidos |
| `OficinaBusinessStageSnapshot` | DIAGNOSIS, APPROVAL, EXECUTION e FINALIZATION; sampleCount, totalDurationMs e média somente quando há amostras |
| `OficinaTelemetryHeartbeat` | Saúde do coletor; não substitui falhas de processamento de OS |

As durações representam criação até diagnóstico, envio do orçamento até aprovação, início da execução até finalização e finalização até entrega. O coletor envia agregados sem CPF, nome, token ou corpo de requisição. `WorkOrderProcessingFailures` continua sendo observado nos logs técnicos; não é inferido do heartbeat.

`generate-dashboards.py` mantém templates portáveis em `dashboards/`. Eles não são exports exatos dos dashboards ajustados na interface; observar unidades, pois templates usam ms e painéis ao vivo podem usar minutos. Em produção sem amostras, a média fica sem dado em vez de informar duração zero. Links dos painéis e alertas estão no [README principal](../../README.md).

O ensaio de alerta contou logs de auth hml, agrupados por statusCode, com limite acima de cinco eventos em um minuto, janela de um minuto e event timer de um minuto. Quarenta respostas 400 e quarenta 401 geraram dois incidentes. Não foram induzidos erros 5xx ou escritas de negócio para esse ensaio; não foi configurada entrega por e-mail/Slack.

## Aceite realizado e limites operacionais

| Item | Resultado registrado |
|---|---|
| Neon/Flyway | Sete migrations por schema hml/prod, TLS verificado |
| Negócio HML | Jornada completa até entregue; segundo cliente recebe 404 ao consultar/decidir OS alheia; cliente recebe 403 em rota administrativa |
| Autenticação | CPF inválido 400; inexistente 401; JWT de outro ambiente rejeitado 401 |
| Produção | Saúde, login, leitura e isolamento JWT; sem escrita de OS sintética |
| Rollback | Mudança de CPU request e restauração Helm preservando OS e digests; não foi rollback de binário |
| Observabilidade | Logs, transações, spans, métricas Kubernetes, etapas e contagens de negócio nos dashboards |
| HTTPS | Dois hosts públicos, certificados válidos e redirecionamento HTTP |
| Vídeo | [Gravado com todos os requisitos](https://drive.google.com/file/d/1OGqlACabTZnHzdbG0k2q0ttf29OQkWOF/view?usp=sharing), conforme confirmação do responsável |

K3s possui um único nó e não oferece alta disponibilidade entre nós. Auth HTTP é container, não função serverless; a implementação Lambda permanece preservada. O frontend não está empacotado no chart OCI. Imagens estão importadas no containerd: registry remoto e CI/CD automático OCI continuam sem execução completa. Schemas compartilham usuário proprietário; isolamento forte requer papéis/grants próprios. Helm não desfaz migrações: usar mudanças aditivas e planejar backup/restauração separadamente.

O smoke de negócio cria dados sintéticos em hml e preserva a OS final para verificação. Executar apenas quando essa escrita for desejada:

```bash
sudo python3 /opt/oficina/oci/business-smoke-vm.py hml
sudo python3 /opt/oficina/oci/business-smoke-vm.py hml --verify-order <UUID_DA_OS_SINTETICA>
sudo bash /opt/oficina/oci/verify-rollback.sh <UUID_DA_OS_SINTETICA>
```

O último comando altera temporariamente a configuração de hml e restaura a revisão anterior. Não usá-lo como consulta de saúde. [Decisões de nuvem, banco, custos e RFCs](../../README.md).

## Verificações reproduzíveis

```bash
python3 deploy/oci/test_secrets.py
python3 deploy/oci/test_chart.py
helm lint deploy/oci/chart -n hml -f deploy/oci/release-values.yaml
```

Referências: [requisitos K3s](https://docs.k3s.io/installation/requirements),
[Java agent em Docker](https://docs.newrelic.com/docs/apm/agents/java-agent/additional-installation/install-new-relic-java-agent-docker/),
[New Relic Helm charts](https://github.com/newrelic/helm-charts/tree/master/charts/nri-bundle),
[Neon e pooling](https://neon.com/docs/connect/connection-pooling).
