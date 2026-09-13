#!/usr/bin/env python3
"""Read-only fallback: classify a candidate key without disclosing credentials."""
import json
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

key = Path('/etc/oficina/newrelic-candidate-key').read_text().strip()
for region, host in [('US', 'api.newrelic.com'), ('EU', 'api.eu.newrelic.com')]:
    request = Request(f'https://{host}/graphql',
                      data=json.dumps({'query': '{ actor { accounts { id name } } }'}).encode(),
                      headers={'API-Key': key, 'Content-Type': 'application/json'})
    try:
        with urlopen(request, timeout=15) as response:
            result = json.load(response)
        accounts = (result.get('data') or {}).get('actor', {}).get('accounts', [])
        print(json.dumps({'region': region, 'user_key_accepted': bool(accounts),
                          'accounts': accounts, 'has_errors': bool(result.get('errors'))}))
    except HTTPError as error:
        print(json.dumps({'region': region, 'http_status': error.code, 'user_key_accepted': False}))
    except (URLError, TimeoutError, ValueError):
        print(json.dumps({'region': region, 'user_key_accepted': False, 'query_failed': True}))
