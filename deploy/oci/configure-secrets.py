#!/usr/bin/env python3
"""Run interactively as root on the VM. No secret is printed or accepted via argv."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
from urllib.parse import parse_qs, unquote, urlencode, urlsplit

ROOT = Path('/etc/oficina')


def neon_config(raw, environment):
    if environment not in ('hml', 'prod'):
        raise ValueError('Environment must be hml or prod.')
    try:
        parsed = urlsplit(raw.strip())
        host = parsed.hostname or ''
        port = parsed.port or 5432
        query = parse_qs(parsed.query)
        database = unquote(parsed.path.lstrip('/'))
        username = unquote(parsed.username or '')
        password = unquote(parsed.password or '')
    except ValueError:
        raise ValueError('Invalid Neon connection URL.') from None
    if (parsed.scheme not in ('postgres', 'postgresql')
            or not re.fullmatch(r'[a-zA-Z0-9.-]+\.neon\.tech', host)
            or '-pooler.' in host or port != 5432
            or not re.fullmatch(r'[a-zA-Z0-9_-]+', database)
            or not username or not password or parsed.fragment
            or query.get('sslmode', [''])[0] not in ('require', 'verify-full')):
        raise ValueError('Use a direct Neon URL (pooling OFF), port 5432 and sslmode=require or verify-full.')
    jdbc_query = urlencode({'currentSchema': environment, 'sslmode': 'verify-full',
                            'sslrootcert': '/etc/ssl/certs/ca-certificates.crt'})
    return {
        'SPRING_DATASOURCE_URL': f'jdbc:postgresql://{host}:5432/{database}?{jdbc_query}',
        'SPRING_DATASOURCE_USERNAME': username,
        'SPRING_DATASOURCE_PASSWORD': password,
        'AUTH_DB_CONFIG': json.dumps({'host': host, 'port': port, 'database': database,
                                      'user': username, 'password': password}),
    }


def write_private(path, value):
    if path.is_symlink():
        raise ValueError('Refusing a symbolic link in configuration.')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(value)
    path.chmod(0o600)


def prepare_directory(directory):
    if directory.is_symlink():
        raise ValueError('Refusing a symbolic-link configuration directory.')
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)


def prepare_runtime(directory, config):
    prepare_directory(directory)
    for name, value in config.items():
        write_private(directory / name, value)
    # Signing and staff identities remain stable when DB configuration is updated.
    for name in ['APP_JWT_SECRET'] + [f'APP_SECURITY_{role}_PASSWORD' for role in
                                    ('MASTER', 'ADMIN', 'ATTENDANT', 'TECHNICIAN', 'WAREHOUSE')]:
        path = directory / name
        if path.is_symlink():
            raise ValueError('Refusing a symbolic link in configuration.')
        if not path.exists():
            write_private(path, secrets.token_urlsafe(48))
        else:
            path.chmod(0o600)


def kube(*args, data=None):
    result = subprocess.run(['/usr/local/bin/k3s', 'kubectl', *args], input=data,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('Kubernetes configuration failed; secret output suppressed.')
    return result.stdout


def publish_secret(namespace, name, directory):
    manifest = kube('-n', namespace, 'create', 'secret', 'generic', name,
                    f'--from-file={directory}', '--dry-run=client', '-o', 'json')
    # Server-side apply avoids saving a second secret copy in last-applied annotations.
    kube('apply', '--server-side', '--field-manager=oficina-secret-setup', '-f', '-', data=manifest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment', choices=('hml', 'prod'))
    parser.add_argument('--new-relic', action='store_true')
    args = parser.parse_args()
    if not args.environment and not args.new_relic:
        parser.error('Choose --environment or --new-relic.')
    if os.geteuid() != 0 or not sys.stdin.isatty():
        raise ValueError('Run with sudo in an interactive SSH terminal.')
    os.umask(0o077)
    prepare_directory(ROOT)
    if args.environment:
        config = neon_config(getpass.getpass('Neon direct connection URL (hidden): '), args.environment)
        directory = ROOT / args.environment
        prepare_runtime(directory, config)
        publish_secret(args.environment, 'oficina-runtime', directory)
        print(f'{args.environment}: runtime Secret configured; values not displayed.')
    if args.new_relic:
        region = input('New Relic region (US/EU): ').strip().upper()
        if region not in ('US', 'EU'):
            raise ValueError('Region must be US or EU.')
        key = getpass.getpass('New Relic INGEST - LICENSE key (hidden): ').strip()
        if len(key) < 20 or not re.fullmatch(r'[A-Za-z0-9_-]+', key):
            raise ValueError('Invalid ingestion-key format.')
        directory = ROOT / 'newrelic'
        prepare_directory(directory)
        write_private(directory / 'licenseKey', key)
        write_private(ROOT / 'newrelic-region', region)
        ns = kube('create', 'namespace', 'newrelic', '--dry-run=client', '-o', 'json')
        kube('apply', '--server-side', '-f', '-', data=ns)
        for namespace in ('newrelic', 'hml', 'prod'):
            publish_secret(namespace, 'newrelic-license', directory)
        print('New Relic Secret configured; telemetry installation is a separate step.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print('Configuration cancelled.', file=sys.stderr)
        sys.exit(130)
    except Exception:
        print('Configuration failed; details suppressed to protect secrets.', file=sys.stderr)
        sys.exit(1)
