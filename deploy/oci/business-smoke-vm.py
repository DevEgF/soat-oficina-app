#!/usr/bin/env python3
"""Run the hml business journey on an OCI VM without exposing credentials or PII."""
import argparse
import json
from pathlib import Path
import secrets
import string
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


API_PORT = 18080
AUTH_PORT = 18081
SECRET_DIR = Path('/etc/oficina/hml')


def request(port, path, method='GET', body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request(f'http://127.0.0.1:{port}{path}', method=method,
                  data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urlopen(req, timeout=35) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else {}
    except HTTPError as error:
        return error.code, {}


def check(label, result, expected, status=None):
    http_status, body = result
    if http_status != expected:
        raise RuntimeError(f'{label}: HTTP {http_status}, expected {expected}')
    if status is not None and body.get('status') != status:
        raise RuntimeError(f'{label}: unexpected OS status')
    print(f'{label}: HTTP {http_status}', flush=True)
    return body


def cpf():
    digits = [secrets.randbelow(10) for _ in range(9)]
    while len(set(digits)) == 1:
        digits = [secrets.randbelow(10) for _ in range(9)]
    for size in (9, 10):
        remainder = sum(number * (size + 1 - index)
                        for index, number in enumerate(digits)) % 11
        digits.append(0 if remainder < 2 else 11 - remainder)
    return ''.join(map(str, digits))


def start_forwards(processes):
    for service, port in [('oficina', API_PORT), ('oficina-auth', AUTH_PORT)]:
        processes.append(subprocess.Popen([
            '/usr/local/bin/k3s', 'kubectl', '-n', 'hml', 'port-forward',
            'service/' + service, f'{port}:8080', '--address=127.0.0.1',
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    for _ in range(30):
        if any(process.poll() is not None for process in processes):
            raise RuntimeError('Port-forward exited before readiness')
        try:
            if request(API_PORT, '/actuator/health')[0] == 200:
                return
        except (URLError, TimeoutError):
            pass
        time.sleep(1)
    raise RuntimeError('API readiness timed out')


def stop_forwards(processes):
    for process in processes:
        if process.poll() is None:
            process.terminate()
    for process in processes:
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def staff_token():
    password = (SECRET_DIR / 'APP_SECURITY_MASTER_PASSWORD').read_text().strip()
    response = request(API_PORT, '/api/public/auth/login', 'POST',
                       {'username': 'master', 'password': password})
    return check('Staff login', response, 200)['accessToken']


def verify_order(order_id, token):
    order = check('Delivered OS verified',
                  request(API_PORT, f'/api/admin/ordens-servico/{order_id}', token=token),
                  200, 'DELIVERED')
    print(f'Final OS status: {order["status"]}', flush=True)
    print(f'Synthetic OS id: {order["id"]}', flush=True)


def run_journey(token):
    stamp = ''.join(secrets.choice(string.ascii_uppercase + string.digits) for _ in range(8))
    plate = ('OC' + secrets.choice(string.ascii_uppercase) + str(secrets.randbelow(10))
             + secrets.choice(string.ascii_uppercase)
             + ''.join(str(secrets.randbelow(10)) for _ in range(2)))
    owner_cpf, other_cpf, unknown_cpf = cpf(), cpf(), cpf()
    while len({owner_cpf, other_cpf, unknown_cpf}) != 3:
        other_cpf, unknown_cpf = cpf(), cpf()

    check('Malformed CPF rejected', request(AUTH_PORT, '/auth/token', 'POST', {'cpf': '123'}), 400)
    check('Nonexistent CPF rejected', request(AUTH_PORT, '/auth/token', 'POST', {'cpf': unknown_cpf}), 401)

    owner = check('Owner customer created', request(API_PORT, '/api/admin/clientes', 'POST', {
        'taxId': owner_cpf, 'name': f'OCI smoke owner {stamp}',
    }, token), 201)
    other = check('Second customer created', request(API_PORT, '/api/admin/clientes', 'POST', {
        'taxId': other_cpf, 'name': f'OCI smoke other {stamp}',
    }, token), 201)
    check('Synthetic vehicle created', request(API_PORT, '/api/admin/veiculos', 'POST', {
        'customerId': owner['id'], 'plate': plate, 'brand': 'OCI',
        'model': f'Smoke {stamp}', 'year': 2026,
    }, token), 201)
    service = check('Synthetic service created', request(API_PORT, '/api/admin/servicos-catalogo', 'POST', {
        'name': f'OCI smoke service {stamp}', 'description': 'Synthetic rollback evidence',
        'priceCents': 100, 'estimatedMinutes': 1,
    }, token), 201)
    order = check('Synthetic OS created', request(API_PORT, '/api/attendant/ordens-servico', 'POST', {
        'customerTaxId': owner_cpf, 'customerName': owner['name'],
        'plate': plate, 'vehicleBrand': 'OCI', 'vehicleModel': f'Smoke {stamp}',
        'vehicleYear': 2026, 'services': [{'catalogServiceId': service['id'], 'quantity': 1}],
        'parts': [],
    }, token), 201, 'RECEIVED')
    order_id, tracking = order['id'], order['trackingCode']
    query = '?' + urlencode({'codigo': tracking})

    owner_token = check('Owner authentication', request(AUTH_PORT, '/auth/token', 'POST',
                                                        {'cpf': owner_cpf}), 200)['accessToken']
    other_token = check('Second customer authentication', request(AUTH_PORT, '/auth/token', 'POST',
                                                                  {'cpf': other_cpf}), 200)['accessToken']
    check('Second customer read rejected',
          request(API_PORT, '/api/customer/os/acompanhar' + query, token=other_token), 404)

    check('Diagnosis started', request(API_PORT,
          f'/api/technician/ordens-servico/{order_id}/iniciar-diagnostico', 'POST', token=token),
          200, 'IN_DIAGNOSIS')
    check('Diagnosis plan submitted', request(API_PORT,
          f'/api/technician/ordens-servico/{order_id}/submeter-plano', 'POST', token=token),
          200, 'PENDING_INTERNAL_APPROVAL')
    check('Internal quote approved', request(API_PORT,
          f'/api/admin/ordens-servico/{order_id}/aprovar-interno', 'POST', token=token),
          200, 'PENDING_APPROVAL')
    check('Second customer approval rejected',
          request(API_PORT, '/api/customer/os/aprovar-orcamento' + query, 'POST', token=other_token), 404)
    check('Owner reads OS', request(API_PORT, '/api/customer/os/acompanhar' + query,
                                   token=owner_token), 200, 'PENDING_APPROVAL')
    check('Owner approves quote', request(API_PORT, '/api/customer/os/aprovar-orcamento' + query,
                                         'POST', token=owner_token), 200, 'AWAITING_PARTS_RELEASE')
    check('Stock release confirmed', request(API_PORT,
          f'/api/warehouse/ordens-servico/{order_id}/confirmar-saida', 'POST', token=token), 204)
    check('OS execution verified', request(API_PORT, f'/api/admin/ordens-servico/{order_id}',
                                          token=token), 200, 'IN_EXECUTION')
    check('Services completed', request(API_PORT,
          f'/api/technician/ordens-servico/{order_id}/concluir-servicos', 'POST', token=token),
          200, 'FINALIZED')
    delivered = check('Delivery registered', request(API_PORT,
          f'/api/attendant/ordens-servico/{order_id}/registrar-entrega', 'POST', token=token),
          200, 'DELIVERED')
    print(f'Final OS status: {delivered["status"]}', flush=True)
    print(f'Synthetic OS id: {delivered["id"]}', flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('environment', choices=['hml'])
    parser.add_argument('--verify-order', metavar='ID')
    args = parser.parse_args(argv)
    processes = []
    try:
        start_forwards(processes)
        token = staff_token()
        if args.verify_order:
            verify_order(args.verify_order, token)
        else:
            run_journey(token)
        print('hml: business smoke passed', flush=True)
    finally:
        stop_forwards(processes)


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error), flush=True)
        raise SystemExit(1)
    except Exception:
        print('Business smoke failed; details suppressed to protect credentials.', flush=True)
        raise SystemExit(1)
