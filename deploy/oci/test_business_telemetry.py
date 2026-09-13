import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest import mock


MODULE_PATH = Path(__file__).with_name("business-telemetry.py")
SPEC = importlib.util.spec_from_file_location("business_telemetry", MODULE_PATH)
telemetry = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(telemetry)


class BusinessTelemetryTest(unittest.TestCase):
    def test_events_include_empty_days_and_all_duration_stages(self):
        rows = [
            {"kind": "daily", "businessDate": "2026-09-06", "createdCount": 0,
             "finalizedCount": 0, "deliveredCount": 0},
            {"kind": "daily", "businessDate": "2026-09-07", "createdCount": 2,
             "finalizedCount": 1, "deliveredCount": 0},
            {"kind": "stage", "stage": "DIAGNOSIS", "sampleCount": 2,
             "totalDurationMs": 3000},
            {"kind": "stage", "stage": "APPROVAL", "sampleCount": 0,
             "totalDurationMs": 0},
            {"kind": "stage", "stage": "EXECUTION", "sampleCount": 1,
             "totalDurationMs": 900},
            {"kind": "stage", "stage": "FINALIZATION", "sampleCount": 1,
             "totalDurationMs": 400},
        ]

        events = telemetry.build_events("hml", rows, 123456)

        daily = [event for event in events if event["eventType"] == "OficinaBusinessDaily"]
        self.assertEqual(0, daily[0]["createdCount"])
        self.assertEqual("2026-09-06", daily[0]["businessDate"])
        diagnosis = next(event for event in events if event.get("stage") == "DIAGNOSIS")
        self.assertEqual(1500, diagnosis["averageDurationMs"])
        empty = next(event for event in events if event.get("stage") == "APPROVAL")
        self.assertNotIn("averageDurationMs", empty)
        self.assertEqual({"DIAGNOSIS", "APPROVAL", "EXECUTION", "FINALIZATION"},
                         {event["stage"] for event in events if "stage" in event})
        self.assertTrue(all(event["environment"] == "hml" for event in events))
        self.assertTrue(all(event["snapshotVersion"] == 1 for event in events))

    def test_sql_defines_pairs_and_excludes_invalid_durations(self):
        sql = telemetry.AGGREGATE_SQL
        self.assertIn("diagnosticado_em >= criado_em", sql)
        self.assertIn("aprovado_em >= orcamento_enviado_em", sql)
        self.assertIn("finalizada_em >= execucao_iniciada_em", sql)
        self.assertIn("entregue_em >= finalizada_em", sql)
        self.assertIn("generate_series(0, 6)", sql)
        self.assertNotIn("SELECT ID", sql.upper())

    @mock.patch.object(telemetry.subprocess, "run")
    def test_psql_uses_environment_for_secret_and_read_only_limits(self, run):
        rows = [
            {"kind": "daily", "businessDate": f"2026-09-{day:02d}", "createdCount": 0,
             "finalizedCount": 0, "deliveredCount": 0}
            for day in range(1, 8)
        ] + [
            {"kind": "stage", "stage": stage, "sampleCount": 0, "totalDurationMs": 0}
            for stage in telemetry.STAGES
        ]
        stdout = ("\n".join(json.dumps(row) for row in rows) + "\n").encode()
        run.return_value = subprocess.CompletedProcess([], 0, stdout=stdout, stderr=b'')
        config = {"host": "db.neon.tech", "port": 5432, "database": "db",
                  "user": "user", "password": "secret"}

        telemetry.query_aggregates(config)

        kwargs = run.call_args.kwargs
        self.assertNotIn("secret", " ".join(run.call_args.args[0]))
        self.assertEqual("secret", kwargs["env"]["PGPASSWORD"])
        self.assertEqual("verify-full", kwargs["env"]["PGSSLMODE"])
        self.assertIn("default_transaction_read_only=on", kwargs["env"]["PGOPTIONS"])
        self.assertEqual(20, kwargs["timeout"])

    @mock.patch.object(telemetry.request, "urlopen")
    def test_delivery_shape_and_api_key_header(self, urlopen):
        response = mock.MagicMock()
        response.status = 200
        response.__enter__.return_value = response
        urlopen.return_value = response
        events = [{"eventType": "OficinaTelemetryHeartbeat", "environment": "prod",
                   "healthy": 1, "timestamp": 10}]

        telemetry.send_events("license-secret", events)

        req = urlopen.call_args.args[0]
        self.assertEqual("license-secret", req.get_header("Api-key"))
        self.assertEqual(events, json.loads(req.data))
        self.assertEqual(15, urlopen.call_args.kwargs["timeout"])

    @mock.patch.object(telemetry, "collect_and_send", side_effect=RuntimeError("secret-value"))
    def test_failure_output_is_sanitized(self, _collect):
        with mock.patch("sys.stderr") as stderr:
            self.assertEqual(1, telemetry.main())
        output = " ".join(str(call) for call in stderr.write.call_args_list)
        self.assertNotIn("secret-value", output)
        self.assertIn("failed", output.lower())


if __name__ == "__main__":
    unittest.main()
