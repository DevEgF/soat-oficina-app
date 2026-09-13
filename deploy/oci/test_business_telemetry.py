import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import unittest
from unittest import mock


MODULE_PATH = Path(__file__).with_name("business-telemetry.py")
SPEC = importlib.util.spec_from_file_location("business_telemetry", MODULE_PATH)
telemetry = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(telemetry)


class BusinessTelemetryTest(unittest.TestCase):
    def test_installer_requires_postgresql_16_client_profile(self):
        installer = Path(__file__).with_name("install-business-telemetry.sh").read_text()
        self.assertIn("dnf module install -y postgresql:16/client", installer)
        self.assertIn("psql \\(PostgreSQL\\) 16\\.", installer)

    def test_postgres_fixture_is_fail_closed(self):
        fixture = Path(__file__).with_name("test_business_telemetry_fixture.sql").read_text()
        self.assertTrue(fixture.lstrip().startswith("CREATE SCHEMA hml;"))
        self.assertNotIn("DROP SCHEMA", fixture.upper())

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

    @mock.patch.object(telemetry.subprocess, "run")
    def test_psql_uses_allowlisted_schema_and_protected_connection(self, run):
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

        telemetry.query_aggregates(config, "hml")

        kwargs = run.call_args.kwargs
        self.assertNotIn("secret", " ".join(run.call_args.args[0]))
        self.assertEqual("secret", kwargs["env"]["PGPASSWORD"])
        self.assertEqual("verify-full", kwargs["env"]["PGSSLMODE"])
        self.assertIn("default_transaction_read_only=on", kwargs["env"]["PGOPTIONS"])
        self.assertIn("search_path=hml", kwargs["env"]["PGOPTIONS"])
        self.assertEqual(20, kwargs["timeout"])

        telemetry.query_aggregates(config, "prod")
        self.assertIn("search_path=prod", run.call_args.kwargs["env"]["PGOPTIONS"])
        with self.assertRaisesRegex(ValueError, "hml or prod"):
            telemetry.query_aggregates(config, "public")

    @unittest.skipUnless(os.environ.get("OFICINA_TELEMETRY_TEST_CONTAINER"),
                         "set OFICINA_TELEMETRY_TEST_CONTAINER for PostgreSQL integration")
    def test_aggregate_sql_executes_against_timestamp_edge_cases(self):
        container = os.environ["OFICINA_TELEMETRY_TEST_CONTAINER"]
        if not re.fullmatch(r"oficina-telemetry-test-[0-9a-f]{12}", container):
            self.fail("integration tests require a uniquely named disposable container")
        label = subprocess.run(
            ["docker", "inspect", "--format",
             '{{index .Config.Labels "oficina.telemetry-fixture"}}', container],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if label.returncode or label.stdout.strip() != b"true":
            self.fail("integration tests require a dedicated telemetry-fixture container")
        fixture = Path(__file__).with_name("test_business_telemetry_fixture.sql").read_bytes()
        setup = subprocess.run(
            ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", "postgres",
             "-v", "ON_ERROR_STOP=1"], input=fixture, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, check=False,
        )
        self.assertEqual(0, setup.returncode, setup.stderr.decode(errors="replace"))
        query = ("SET search_path TO hml;\n" + telemetry.AGGREGATE_SQL).encode()
        result = subprocess.run(
            ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", "postgres",
             "--no-psqlrc", "--quiet", "--tuples-only", "--no-align",
             "-v", "ON_ERROR_STOP=1"], input=query, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        rows = [json.loads(line) for line in result.stdout.decode().splitlines()
                if line.startswith("{")]
        daily = [row for row in rows if row["kind"] == "daily"]
        stages = {row["stage"]: row for row in rows if row["kind"] == "stage"}
        self.assertEqual(7, len(daily))
        self.assertEqual(6, sum(row["createdCount"] == 0 for row in daily))
        today = next(row for row in daily if row["createdCount"] > 0)
        self.assertEqual((3, 2, 2), (today["createdCount"], today["finalizedCount"],
                                    today["deliveredCount"]))
        self.assertEqual(set(telemetry.STAGES), set(stages))
        for stage in stages.values():
            self.assertEqual(1, stage["sampleCount"])
            self.assertEqual(60000, stage["totalDurationMs"])

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
