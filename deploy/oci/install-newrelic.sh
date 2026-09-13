#!/usr/bin/env bash
set -euo pipefail
export PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export KUBECONFIG=${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}
directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
region=$(cat /etc/oficina/newrelic-region)
[[ $region == US || $region == EU ]] || exit 2
kubectl get node oficina-oci -o name >/dev/null
kubectl -n newrelic get secret newrelic-license -o name >/dev/null
# Keep offsets persistent without granting containers write access to var_log_t.
install -d -m 0700 /var/lib/oficina-fluentbit
if command -v selinuxenabled >/dev/null && selinuxenabled; then
  chcon -R -t container_file_t /var/lib/oficina-fluentbit
fi
helm repo add newrelic https://helm-charts.newrelic.com
flags=()
if [[ $region == EU ]]; then
  flags+=(--set-string newrelic-logging.endpoint=https://log-api.eu.newrelic.com/log/v1)
  flags+=(--set-string newrelic-logging.metricsEndpoint=metric-api.eu.newrelic.com)
  flags+=(--set-string newrelic-infrastructure.common.agentConfig.region=EU)
fi
helm upgrade --install newrelic-bundle newrelic/nri-bundle --version 8.0.24 \
  --namespace newrelic -f "$directory/newrelic-values.yaml" "${flags[@]}" \
  --atomic --wait --timeout 10m --history-max 3
kubectl -n newrelic get pods
# Pod readiness is not proof that telemetry has reached the New Relic account.
