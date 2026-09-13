#!/usr/bin/env bash
set -euo pipefail

export PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export KUBECONFIG=${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}

[[ ${EUID:-$(id -u)} -eq 0 ]] || { echo 'Run as root.' >&2; exit 1; }

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
manifest_dir="$script_dir/https"
chart_dir="$script_dir/chart"

kubectl get node oficina-oci -o name >/dev/null
for environment in hml prod; do
  kubectl get namespace "$environment" -o name >/dev/null
  helm status oficina --namespace "$environment" >/dev/null
done

helm upgrade --install cert-manager \
  oci://quay.io/jetstack/charts/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.20.3 \
  --set crds.enabled=true \
  --set-json 'resources.requests={"cpu":"50m","memory":"64Mi"}' \
  --set-json 'resources.limits={"cpu":"500m","memory":"256Mi"}' \
  --set-json 'webhook.resources.requests={"cpu":"20m","memory":"32Mi"}' \
  --set-json 'webhook.resources.limits={"cpu":"200m","memory":"128Mi"}' \
  --set-json 'cainjector.resources.requests={"cpu":"20m","memory":"64Mi"}' \
  --set-json 'cainjector.resources.limits={"cpu":"200m","memory":"256Mi"}' \
  --set-json 'startupapicheck.resources.requests={"cpu":"10m","memory":"32Mi"}' \
  --set-json 'startupapicheck.resources.limits={"cpu":"100m","memory":"64Mi"}' \
  --wait --atomic --timeout 10m

kubectl apply -f "$manifest_dir/cluster-issuer.yaml"
kubectl wait --for=condition=Ready clusterissuer/letsencrypt-production --timeout=3m
kubectl apply -f "$manifest_dir/certificates.yaml"
kubectl apply -f "$manifest_dir/redirects.yaml"

kubectl -n hml wait --for=condition=Ready certificate/oficina-tls --timeout=10m
kubectl -n prod wait --for=condition=Ready certificate/oficina-tls --timeout=10m

for environment in hml prod; do
  if [[ $environment == hml ]]; then
    host=hml.129.213.121.122.sslip.io
  else
    host=oficina.129.213.121.122.sslip.io
  fi
  helm upgrade oficina "$chart_dir" --namespace "$environment" --reuse-values \
    --set ingress.enabled=true \
    --set-string ingress.host="$host" \
    --set-string ingress.tlsSecret=oficina-tls \
    --no-hooks --wait --atomic --timeout 10m
done

kubectl -n hml get certificate oficina-tls -o name
kubectl -n prod get certificate oficina-tls -o name
echo 'OCI HTTPS installation passed.'
