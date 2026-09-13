#!/usr/bin/env python3
"""Run on the VM as root; credentials remain in memory and requests use loopback."""
import argparse
import json
from pathlib import Path
import secrets
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def request(port, path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(f'http://127.0.0.1:{port}{path}', method=method,
                  data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urlopen(req, timeout=35) as response:
            return response.status, json.loads(response.read() or b'{}')
    except HTTPError as error:
        return error.code, {}


def check(label, result, expected):
    status, body = result
    if status != expected:
        raise RuntimeError(f'{label}: HTTP {status}, expected {expected}')
    print(f'{label}: HTTP {status}', flush=True)
    return body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('environment', choices=['hml', 'prod'])
    args = parser.parse_args()
    processes = []
    customer_id = None
    staff = None
    try:
        for service, port in [('oficina', 18080), ('oficina-auth', 18081)]:
            processes.append(subprocess.Popen(['/usr/local/bin/k3s', 'kubectl', '-n', args.environment,
                                               'port-forward', 'service/' + service, f'{port}:8080',
                                               '--address=127.0.0.1'], stdout=subprocess.DEVNULL,
                                              stderr=subprocess.DEVNULL))
        for attempt in range(30):
            try:
                if request(18080, '/actuator/health')[0] == 200:
                    break
            except (URLError, TimeoutError):
                pass
            time.sleep(1)
        health = check('API health', request(18080, '/actuator/health'), 200)
        assert health.get('status') == 'UP'
        check('Auth health', request(18081, '/health'), 200)
        check('Anonymous admin rejected', request(18080, '/api/admin/clientes'), 401)
        password = (Path('/etc/oficina') / args.environment / 'APP_SECURITY_MASTER_PASSWORD').read_text()
        staff = check('Staff login', request(18080, '/api/public/auth/login', 'POST',
                                            {'username': 'master', 'password': password}), 200)['accessToken']
        check('Staff database read', request(18080, '/api/admin/clientes', token=staff), 200)
        check('Malformed CPF rejected', request(18081, '/auth/token', 'POST', {'cpf': '123'}), 400)
        if args.environment == 'prod':
            processes.append(subprocess.Popen(['/usr/local/bin/k3s', 'kubectl', '-n', 'hml',
                                               'port-forward', 'service/oficina', '18082:8080',
                                               '--address=127.0.0.1'], stdout=subprocess.DEVNULL,
                                              stderr=subprocess.DEVNULL))
            for attempt in range(30):
                try:
                    if request(18082, '/actuator/health')[0] == 200:
                        break
                except (URLError, TimeoutError):
                    pass
                time.sleep(1)
            hml_password = Path('/etc/oficina/hml/APP_SECURITY_MASTER_PASSWORD').read_text()
            hml_token = check('Hml reference login', request(18082, '/api/public/auth/login', 'POST',
                              {'username': 'master', 'password': hml_password}), 200)['accessToken']
            check('Hml token rejected by prod', request(18080, '/api/admin/clientes', token=hml_token), 401)
            check('Prod token rejected by hml', request(18082, '/api/admin/clientes', token=staff), 401)
        if args.environment == 'hml':
            digits = [secrets.randbelow(10) for _ in range(9)]
            for size in (9, 10):
                remainder = sum(n * (size + 1 - i) for i, n in enumerate(digits)) % 11
                digits.append(0 if remainder < 2 else 11 - remainder)
            cpf = ''.join(map(str, digits))
            customer_id = check('Synthetic customer created', request(18080, '/api/admin/clientes', 'POST',
                                {'taxId': cpf, 'name': 'OCI smoke synthetic'}, staff), 201)['id']
            customer = check('Customer PostgreSQL authentication', request(18081, '/auth/token', 'POST',
                              {'cpf': cpf}), 200)['accessToken']
            check('Customer admin access rejected', request(18080, '/api/admin/clientes', token=customer), 403)
            check('Customer JWT accepted by API', request(18080, '/api/customer/os/acompanhar?codigo=oci-smoke-absent',
                                                        token=customer), 404)
        print(f'{args.environment}: smoke passed; no credentials displayed.', flush=True)
    finally:
        try:
            if customer_id:
                check('Synthetic customer removed', request(18080, '/api/admin/clientes/' + customer_id,
                                                           'DELETE', token=staff), 204)
        finally:
            for process in processes:
                process.terminate()
                process.wait(timeout=10)


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(1)
    except Exception:
        print('Smoke failed; details suppressed to protect credentials.')
        raise SystemExit(1)
