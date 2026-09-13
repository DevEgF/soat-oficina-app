#!/usr/bin/env bash
set -euo pipefail
export PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
order=${1:?Delivered synthetic hml order ID required}
[[ $order =~ ^[a-f0-9-]{36}$ ]] || exit 2
revision=$(helm history oficina -n hml -o json | python3 -c 'import json,sys; print(max(x["revision"] for x in json.load(sys.stdin) if x["status"] == "deployed"))')
python3 "$directory/business-smoke-vm.py" hml --verify-order "$order"
helm upgrade oficina "$directory/chart" -n hml --reuse-values --set resources.requests.cpu=210m --no-hooks --atomic --wait --timeout 10m
# Always restore the preceding configuration, including if the verification fails.
verification=0
python3 "$directory/business-smoke-vm.py" hml --verify-order "$order" || verification=$?
helm rollback oficina "$revision" -n hml --no-hooks --wait --timeout 10m
python3 "$directory/business-smoke-vm.py" hml --verify-order "$order"
helm history oficina -n hml
exit "$verification"
