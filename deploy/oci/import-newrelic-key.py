#!/usr/bin/env python3
"""Accept an authorized key over SSH stdin, validate region, and publish without echo."""
import importlib.util
import getpass
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

spec = importlib.util.spec_from_file_location('setup', Path(__file__).with_name('configure-secrets.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


def main():
    if os.geteuid() != 0:
        return 2
    os.umask(0o077)
    key = (getpass.getpass('New Relic key (hidden): ') if sys.stdin.isatty() else sys.stdin.readline(4096)).strip()
    if not re.fullmatch(r'[A-Za-z0-9_-]{20,256}', key):
        print('Invalid key format; value suppressed.')
        return 2
    setup.prepare_directory(setup.ROOT)
    setup.write_private(setup.ROOT / 'newrelic-candidate-key', key)
    payload = json.dumps([{'metrics': [{'name': 'oficina.bootstrap.connection', 'type': 'gauge',
                                      'value': 1, 'timestamp': int(time.time()),
                                      'attributes': {'clusterName': 'oficina-oci'}}]}]).encode()
    for region, hostname in [('US', 'metric-api.newrelic.com'), ('EU', 'metric-api.eu.newrelic.com')]:
        request = Request(f'https://{hostname}/metric/v1', data=payload,
                          headers={'Api-Key': key, 'Content-Type': 'application/json'})
        try:
            with urlopen(request, timeout=15) as response:
                status = response.status
        except HTTPError as error:
            status = error.code
        except (URLError, TimeoutError):
            print(f'{region}: endpoint unreachable; key value suppressed.')
            continue
        print(f'{region}: metric API HTTP {status}.')
        if status in (200, 202):
            directory = setup.ROOT / 'newrelic'
            setup.prepare_directory(directory)
            setup.write_private(directory / 'licenseKey', key)
            setup.write_private(setup.ROOT / 'newrelic-region', region)
            for namespace in ('newrelic', 'hml', 'prod'):
                setup.publish_secret(namespace, 'newrelic-license', directory)
            print(f'Ingestion credential accepted in {region}; Secrets configured. Agent handshake still required.')
            return 0
    print('Credential was not accepted for metric ingestion. Agents were not installed.')
    return 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        print('Configuration failed; response details suppressed to protect credentials.')
        sys.exit(1)
