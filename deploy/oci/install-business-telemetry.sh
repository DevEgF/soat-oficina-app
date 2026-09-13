#!/usr/bin/env bash
set -euo pipefail
export PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
[[ $(id -u) -eq 0 ]] || { echo 'Run as root.' >&2; exit 1; }

directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for path in /etc/oficina/hml/AUTH_DB_CONFIG /etc/oficina/prod/AUTH_DB_CONFIG /etc/oficina/newrelic/licenseKey; do
  [[ -f $path && ! -L $path ]] || { echo 'Required protected configuration is missing.' >&2; exit 1; }
  chmod 0600 "$path"
done
chmod 0700 /etc/oficina /etc/oficina/hml /etc/oficina/prod /etc/oficina/newrelic

dnf install -y python3 postgresql
install -d -o root -g root -m 0700 /usr/local/libexec/oficina
install -o root -g root -m 0700 "$directory/business-telemetry.py" /usr/local/libexec/oficina/business-telemetry.py
install -o root -g root -m 0644 "$directory/systemd/oficina-business-telemetry.service" /etc/systemd/system/oficina-business-telemetry.service
install -o root -g root -m 0644 "$directory/systemd/oficina-business-telemetry.timer" /etc/systemd/system/oficina-business-telemetry.timer
systemctl daemon-reload
systemctl enable --now oficina-business-telemetry.timer
