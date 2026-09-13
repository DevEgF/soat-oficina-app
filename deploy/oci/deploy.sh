#!/usr/bin/env bash
set -euo pipefail
export PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
environment=${1:?Usage: deploy.sh hml-or-prod image-repository sha256:digest}
repository=${2:?Image repository is required}
digest=${3:?Image digest is required}
shift 3
[[ $environment == hml || $environment == prod ]] || exit 2
[[ $digest =~ ^sha256:[a-f0-9]{64}$ ]] || exit 2
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
export KUBECONFIG=${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}
kubectl get node oficina-oci -o name >/dev/null
kubectl -n "$environment" get secret oficina-runtime -o name >/dev/null
if [[ $environment == prod ]]; then
  hml_image=$(kubectl -n hml get deployment oficina -o jsonpath='{.spec.template.spec.containers[0].image}')
  [[ $hml_image == "$repository@$digest" ]] || { echo 'Production must use the image deployed in hml.' >&2; exit 1; }
  kubectl -n hml rollout status deployment/oficina --timeout=120s
fi
helm upgrade --install oficina "$script_dir/chart" "$@" --namespace "$environment" \
  --set-string environment="$environment" \
  --set-string image.repository="$repository" --set-string image.digest="$digest" \
  --wait --atomic --timeout 10m --history-max 5
kubectl -n "$environment" rollout status deployment/oficina --timeout=120s
