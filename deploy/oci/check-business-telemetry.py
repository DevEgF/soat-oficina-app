#!/usr/bin/env python3
"""Sanitized runtime diagnostic: print stages and HTTP status, never error bodies."""
import importlib.util
from pathlib import Path
import time
from urllib.error import HTTPError

spec = importlib.util.spec_from_file_location("collector", "/usr/local/libexec/oficina/business-telemetry.py")
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)
original_run = collector.subprocess.run
def diagnostic_run(*args, **kwargs):
    result = original_run(*args, **kwargs)
    if result.returncode:
        stderr = result.stderr.decode(errors="replace").lower()
        categories = [label for label in ("ssl", "certificate", "password authentication", "relation", "column", "permission denied", "unrecognized configuration", "unsupported", "timeout", "could not translate", "connection") if label in stderr]
        print("Database failure categories:", ", ".join(categories) or "unclassified")
    return result
collector.subprocess.run = diagnostic_run
for environment in collector.ENVIRONMENTS:
    stage = "configuration"
    try:
        config = collector.read_json(collector.CONFIG_ROOT / environment / "AUTH_DB_CONFIG")
        stage = "database"
        rows = collector.query_aggregates(config, environment)
        print(environment, stage, "OK", len(rows), "aggregate rows")
        stage = "event delivery"
        key = (collector.CONFIG_ROOT / "newrelic" / "licenseKey").read_text().strip()
        collector.send_events(key, collector.build_events(environment, rows, int(time.time())))
        print(environment, stage, "OK")
    except HTTPError as error:
        print(environment, stage, "HTTP", error.code)
    except Exception as error:
        print(environment, stage, type(error).__name__)
