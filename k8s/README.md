# Manifestos Kubernetes — Oficina

## Contexto atual

Estes manifestos são o laboratório local com PostgreSQL em pod. A implantação OCI usa o chart separado em deploy/oci/chart e banco Neon; estes manifests não representam os recursos ativos de hml/prod.

O [README principal](../README.md) reúne RFCs, justificativa AWS → Oracle, escolha PostgreSQL/Neon, arquitetura, dashboards, alertas e situação do vídeo.

Deploy do monólito (Spring Boot) + PostgreSQL com autoescala horizontal (HPA).
Pensado para cluster **local** (kind/minikube), mas portável para cloud.

## Arquivos (ordem de aplicação)

| Arquivo | Recurso |
| ------- | ------- |
| `00-namespace.yaml` | Namespace `oficina` |
| `10-configmap.yaml` | Config não sensível da app e do banco |
| `11-secret.example.yaml` | **Template** de segredos (copie para `11-secret.yaml`) |
| `20-db.yaml` | PostgreSQL: PVC + Deployment + Service ClusterIP |
| `30-app.yaml` | App: Deployment (initContainer aguarda o banco) + Service |
| `40-hpa.yaml` | HorizontalPodAutoscaler (CPU 70% / memória 80%, 1→5 réplicas) |

## Pré-requisitos

- Cluster local com **metrics-server** habilitado (o HPA depende dele):
  - minikube: `minikube addons enable metrics-server`
  - kind: instalar metrics-server manualmente (com `--kubelet-insecure-tls`).
- Imagem publicada no GHCR pelo CI/CD (`ghcr.io/devegf/soat-tech-challenge-oficina`).
  Em cluster local, alternativamente carregue a imagem buildada:
  - kind: `kind load docker-image ghcr.io/devegf/soat-tech-challenge-oficina:latest`
  - minikube: `minikube image load ghcr.io/devegf/soat-tech-challenge-oficina:latest`

## Deploy

```bash
# 1. Prepare o secret real (NÃO commitado — vide .gitignore)
cp k8s/11-secret.example.yaml k8s/11-secret.yaml
# edite k8s/11-secret.yaml: SPRING_DATASOURCE_PASSWORD == POSTGRES_PASSWORD

# 2. Aplique tudo
kubectl apply -f k8s/

# 3. Acompanhe
kubectl -n oficina get pods -w
kubectl -n oficina get hpa
```

## Acesso local

```bash
kubectl -n oficina port-forward svc/oficina-app 8081:8080
# http://localhost:8081/actuator/health  -> {"status":"UP"}
```

> Se o cluster foi criado via Terraform (`infra/`), a porta `8080` do host já está
> reservada pelo `extra_port_mappings` do kind — use outra porta local (ex.: `8081`)
> no `port-forward`.

## Validar autoescala (demo do HPA)

```bash
# Gere carga (ex.: hey, ab ou um loop de requisições) e observe:
kubectl -n oficina get hpa oficina-app -w
kubectl -n oficina get pods -w
```
