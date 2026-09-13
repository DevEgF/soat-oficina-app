#!/usr/bin/env python3
"""Publish read-only aggregate work-order telemetry from Neon to New Relic."""

import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib import request


ACCOUNT_ID = "8439293"
EVENT_URL = f"https://insights-collector.newrelic.com/v1/accounts/{ACCOUNT_ID}/events"
CONFIG_ROOT = Path("/etc/oficina")
ENVIRONMENTS = ("hml", "prod")
STAGES = ("DIAGNOSIS", "APPROVAL", "EXECUTION", "FINALIZATION")

AGGREGATE_SQL = r"""
WITH parameters AS (
  SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date AS today
), days AS (
  SELECT (today - day_offset::integer)::date AS business_date
  FROM parameters CROSS JOIN generate_series(0, 6) AS series(day_offset)
), daily AS (
  SELECT d.business_date,
    count(*) FILTER (WHERE (o.criado_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date) AS created_count,
    count(*) FILTER (WHERE (o.finalizada_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date) AS finalized_count,
    count(*) FILTER (WHERE (o.entregue_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date) AS delivered_count
  FROM days d
  LEFT JOIN ordens_servico o ON
    (o.criado_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date OR
    (o.finalizada_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date OR
    (o.entregue_em AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date = d.business_date
  GROUP BY d.business_date
), stage_samples(stage, completed_at, duration_ms) AS (
  SELECT 'DIAGNOSIS', diagnosticado_em,
    extract(epoch FROM (diagnosticado_em - criado_em)) * 1000
  FROM ordens_servico WHERE diagnosticado_em IS NOT NULL AND diagnosticado_em >= criado_em
  UNION ALL
  SELECT 'APPROVAL', aprovado_em,
    extract(epoch FROM (aprovado_em - orcamento_enviado_em)) * 1000
  FROM ordens_servico WHERE orcamento_enviado_em IS NOT NULL AND aprovado_em IS NOT NULL
    AND aprovado_em >= orcamento_enviado_em
  UNION ALL
  SELECT 'EXECUTION', finalizada_em,
    extract(epoch FROM (finalizada_em - execucao_iniciada_em)) * 1000
  FROM ordens_servico WHERE execucao_iniciada_em IS NOT NULL AND finalizada_em IS NOT NULL
    AND finalizada_em >= execucao_iniciada_em
  UNION ALL
  SELECT 'FINALIZATION', entregue_em,
    extract(epoch FROM (entregue_em - finalizada_em)) * 1000
  FROM ordens_servico WHERE finalizada_em IS NOT NULL AND entregue_em IS NOT NULL
    AND entregue_em >= finalizada_em
), stage_names(stage) AS (
  VALUES ('DIAGNOSIS'), ('APPROVAL'), ('EXECUTION'), ('FINALIZATION')
), stage_totals AS (
  SELECT n.stage, count(s.duration_ms) AS sample_count,
    coalesce(round(sum(s.duration_ms)), 0) AS total_duration_ms
  FROM stage_names n CROSS JOIN parameters p
  LEFT JOIN stage_samples s ON s.stage = n.stage
    AND (s.completed_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/Sao_Paulo')::date
      BETWEEN p.today - 6 AND p.today
  GROUP BY n.stage
)
SELECT json_build_object('kind', 'daily', 'businessDate', to_char(business_date, 'YYYY-MM-DD'),
  'createdCount', created_count, 'finalizedCount', finalized_count, 'deliveredCount', delivered_count)::text
FROM daily
UNION ALL
SELECT json_build_object('kind', 'stage', 'stage', stage, 'sampleCount', sample_count,
  'totalDurationMs', total_duration_ms)::text
FROM stage_totals;
"""


def read_json(path):
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError("Invalid database configuration.")
    return value


def query_aggregates(config):
    required = ("host", "port", "database", "user", "password")
    if any(key not in config for key in required):
        raise ValueError("Invalid database configuration.")
    process_env = os.environ.copy()
    process_env.update({
        "PGHOST": str(config["host"]), "PGPORT": str(config["port"]),
        "PGDATABASE": str(config["database"]), "PGUSER": str(config["user"]),
        "PGPASSWORD": str(config["password"]), "PGSSLMODE": "verify-full",
        "PGSSLROOTCERT": "/etc/pki/tls/certs/ca-bundle.crt",
        "PGOPTIONS": "-c default_transaction_read_only=on -c statement_timeout=15000",
    })
    result = subprocess.run(
        ["psql", "--no-psqlrc", "--quiet", "--tuples-only", "--no-align",
         "--set", "ON_ERROR_STOP=1"], input=AGGREGATE_SQL.encode(),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=process_env,
        timeout=20, check=False,
    )
    if result.returncode:
        raise RuntimeError("Database aggregation failed.")
    try:
        rows = [json.loads(line) for line in result.stdout.decode().splitlines() if line.strip()]
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise RuntimeError("Database aggregation returned invalid data.") from None
    if (sum(row.get("kind") == "daily" for row in rows) != 7
            or {row.get("stage") for row in rows if row.get("kind") == "stage"} != set(STAGES)):
        raise RuntimeError("Database aggregation returned incomplete data.")
    return rows


def build_events(environment, rows, collected_at):
    events = []
    for row in rows:
        if row.get("kind") == "daily":
            events.append({
                "eventType": "OficinaBusinessDaily", "environment": environment,
                "businessDate": row["businessDate"], "createdCount": int(row["createdCount"]),
                "finalizedCount": int(row["finalizedCount"]),
                "deliveredCount": int(row["deliveredCount"]), "snapshotVersion": 1,
                "timestamp": collected_at,
            })
        elif row.get("kind") == "stage" and row.get("stage") in STAGES:
            sample_count = int(row["sampleCount"])
            total_duration = int(row["totalDurationMs"])
            event = {
                "eventType": "OficinaBusinessStageSnapshot", "environment": environment,
                "stage": row["stage"], "sampleCount": sample_count,
                "totalDurationMs": total_duration, "windowDays": 7, "snapshotVersion": 1,
                "timestamp": collected_at,
            }
            if sample_count > 0:
                event["averageDurationMs"] = round(total_duration / sample_count)
            events.append(event)
    return events


def send_events(license_key, events):
    payload = json.dumps(events, separators=(",", ":")).encode()
    req = request.Request(EVENT_URL, data=payload, method="POST", headers={
        "API-Key": license_key, "Content-Type": "application/json",
        "User-Agent": "oficina-business-telemetry/1",
    })
    with request.urlopen(req, timeout=15) as response:
        if not 200 <= response.status < 300:
            raise RuntimeError("New Relic delivery failed.")


def collect_and_send():
    license_key = (CONFIG_ROOT / "newrelic" / "licenseKey").read_text(encoding="utf-8").strip()
    if not license_key:
        raise ValueError("New Relic configuration is empty.")
    collected_at = int(time.time())
    for environment in ENVIRONMENTS:
        config = read_json(CONFIG_ROOT / environment / "AUTH_DB_CONFIG")
        events = build_events(environment, query_aggregates(config), collected_at)
        send_events(license_key, events)
        send_events(license_key, [{
            "eventType": "OficinaTelemetryHeartbeat", "environment": environment,
            "healthy": 1, "timestamp": int(time.time()),
        }])


def main():
    try:
        collect_and_send()
        return 0
    except Exception:
        print("Business telemetry collection failed; details suppressed.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
