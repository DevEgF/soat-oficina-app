#!/usr/bin/env python3
"""Import an authorized direct Neon URL over SSH stdin without displaying it."""
import importlib.util
import os
from pathlib import Path
import sys


def main():
    if os.geteuid() != 0:
        return 2
    os.umask(0o077)
    spec = importlib.util.spec_from_file_location('setup', Path(__file__).with_name('configure-secrets.py'))
    setup = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(setup)
    raw = sys.stdin.readline(4096).strip()
    configs = {env: setup.neon_config(raw, env) for env in ('hml', 'prod')}
    setup.prepare_directory(setup.ROOT)
    for environment, config in configs.items():
        directory = setup.ROOT / environment
        setup.prepare_runtime(directory, config)
        setup.publish_secret(environment, 'oficina-runtime', directory)
        print(f'{environment}: runtime Secret configured; database connectivity not yet validated.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        print('Configuration failed; details suppressed to protect credentials.')
        sys.exit(1)
