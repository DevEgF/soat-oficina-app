# Infraestrutura (Terraform) — Cluster local kind

## Contexto atual

Este diretório continua sendo o laboratório Terraform/kind da Fase 2, com PostgreSQL dentro do cluster. A implantação atual usa K3s na Oracle e banco externo Neon; não aplique este Terraform para alterar a VM OCI.

O [README principal](../README.md) reúne RFCs, justificativa AWS → Oracle, escolha PostgreSQL/Neon, arquitetura, dashboards, alertas e situação do vídeo.

Provisiona um cluster **Kubernetes local com [kind](https://kind.sigs.k8s.io/)**
para rodar a aplicação Oficina + PostgreSQL com autoescala (HPA).

> Decisão registrada: **cluster local (kind)**. Suficiente para a entrega da Fase 2,
> gratuito e reproduzível. Para alta disponibilidade real, migrar para EKS/GKE/AKS
> trocaria o provider `kind` por um provider de cloud — a estrutura dos manifestos
> em `../k8s` permanece a mesma.

## Recursos criados

| Recurso | Descrição |
| ------- | --------- |
| `kind_cluster.oficina` | Cluster kind (1 nó control-plane) com porta `30080`→`host_http_port` |
| `null_resource.metrics_server` | Instala o metrics-server + patch `--kubelet-insecure-tls` (necessário p/ HPA) |
| `null_resource.workloads` | `kubectl apply -f ../k8s` — **ligado por padrão** (`bootstrap_workloads = true`) |

O **banco de dados** (PostgreSQL) é provisionado como workload no cluster
(`../k8s/20-db.yaml`, com `PersistentVolumeClaim`) e sobe automaticamente no
`terraform apply` — é o recurso "Banco de Dados" exigido pela IaC da Fase 2.

## Pré-requisitos

- [Docker](https://www.docker.com/) em execução (o kind cria o nó como contêiner).
- [`kind`](https://kind.sigs.k8s.io/docs/user/quick-start/#installation), [`kubectl`] e [`terraform`] (>= 1.5) no PATH.

## Uso

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # ajuste se necessário

terraform init
terraform plan
terraform apply          # cria o cluster + metrics-server + workloads (banco, app, HPA)

# Aponte o kubectl para o cluster criado
export KUBECONFIG="$(terraform output -raw kubeconfig_path)"   # bash
# $env:KUBECONFIG = (terraform output -raw kubeconfig_path)     # PowerShell

# Confira o banco e a app subindo
kubectl -n oficina get pods,svc,hpa
```

### Segredos e imagem da aplicação

- **Segredos:** para dev local não é preciso criar nada — o `kubectl apply -f ../k8s`
  aplica o `k8s/11-secret.example.yaml` (senhas placeholder autoconsistentes). Para
  uso real, crie `k8s/11-secret.yaml` (gitignored) a partir do `.example`; por ser
  aplicado depois (ordem alfabética), ele **sobrescreve** o template.
- **Imagem da app:** o `30-app.yaml` referencia `ghcr.io/devegf/...:latest`. Em kind,
  carregue a imagem local para evitar pull do registry:
  ```bash
  kind load docker-image ghcr.io/devegf/soat-tech-challenge-oficina:latest --name oficina
  ```
  O **banco sobe independentemente** da imagem da app.

### Separar infra × workloads (opcional)

Para gerenciar os workloads só via `kubectl`/CI-CD, defina `bootstrap_workloads = false`
no `terraform.tfvars` e aplique os manifestos manualmente com `kubectl apply -f k8s/`.

## Destruir

```bash
terraform destroy
```

## Notas

- `terraform plan` precisa apenas dos providers (roda offline após `init`); a criação
  real do cluster exige Docker no ar.
- O estado é **local** (`terraform.tfstate`) — adequado para a entrega. Em cloud,
  configurar backend remoto (S3/GCS) seria o próximo passo.
