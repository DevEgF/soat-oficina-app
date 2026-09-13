# Continuação OCI, K3s, Neon e New Relic

Implementação adicional em `feature/oci-k3s`. Os arquivos AWS existentes não foram
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
  A jornada completa de OS e autorização entre proprietários ainda não foi repetida.

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

Sem domínio/TLS, manter ingress desabilitado e usar port-forward por SSH para
validação privada. O Traefik respondeu 404 localmente; a tentativa HTTP externa
ao IP da VM não conectou. Verificar regras OCI para 80/443 e configurar domínio e
certificado antes de habilitar ingress. Não abrir 6443/10250 para a internet.

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
As métricas de negócio EMF existentes não viram automaticamente métricas New Relic;
dashboards/alertas de negócio ainda precisam de adaptação e comprovação.

## Limites do que está pronto

- A jornada completa de OS, autorização entre proprietários e rollback OCI ainda
  precisam de validação; conexão Neon, migrações e ingestão New Relic foram comprovadas.
- O adaptador HTTP de auth usa o domínio/JWT existentes, mas **não é serverless**.
  A implementação Lambda e suas evidências AWS permanecem a referência desse critério.
- K3s de nó único não oferece alta disponibilidade entre nós.
- O frontend não foi empacotado neste chart; o escopo atual é API e autenticação.
- CI/CD automático OCI, publicação em registry, smoke/rollback integrado e HTTPS
  público ainda não estão concluídos. O deploy atual é script protegido via SSH.
- A métrica de Finalização, painel diário e complementação do vídeo do aceite
  continuam pendentes; instalar New Relic não resolve automaticamente essas lacunas.

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
