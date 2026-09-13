# OCI acceptance continuation

Spec: ../specs/2026-09-12-oci-k3s-design.md

## Global Constraints

No AWS operations or modifications to AWS deployment files. No plaintext secret
output, Git persistence or credentials passed on command lines. Keep SELinux and
firewalld. Do not claim a containerized auth service is serverless.
Reuse the existing feature/oci-k3s worktree. Only OCI-specific files may change.
Runtime production changes must preserve data. Synthetic business writes are in hml only.

### Task 1: Reproducible private business journey

Own deploy/oci/business-smoke-vm.py and its focused tests, nothing else.
Implement a Python standard-library smoke runner for root on the VM, hml only.
Use deploy/oci/smoke-vm.py patterns for loopback kubectl port-forward and reading
staff secrets from /etc/oficina/hml. Do not print request/response bodies, tokens,
passwords, or CPF. Print only check names, statuses, final OS status and synthetic
OS id. Derive actual endpoint payloads from the existing controllers, DTOs,
postman collections and scripts/smoke-local-fixtures.mjs. Do not change app code.
Create uniquely named synthetic customers/vehicle/service/OS through authenticated
APIs and drive OS through its required states to DELIVERED. Validate a second
customer cannot read or approve the first customer's OS, and an authorized owner
can. Validate malformed/nonexistent CPF rejection as supported by the API.
Avoid email delivery: omit customer email, never call external notification APIs.
Keep delivered OS fixture as explicit evidence with id in stdout; do not delete
historical OS or unrelated records. Report dependent retained synthetic fixtures.
Support a read-only --verify-order ID invocation checking the delivered record with
staff authentication, for later rollback validation. Clean port-forward processes
in finally, fail closed on unexpected responses. Focused tests should cover behavior
such as request/cleanup failure and hml-only enforcement, not mirror source text.
Run local tests and syntax checks; controller will execute runtime smoke over SSH.
Commit only owned files and write report to the task report path supplied at dispatch.

### Task 2: Public HTTPS

Inspect OCI ingress rules and VM reachability. Use user domain if supplied;
otherwise validate a free IP-based hostname. Configure a trusted certificate and
separate hml/prod hosts using additive OCI files and existing Traefik. Keep SSH
administration; never expose Kubernetes API to the internet. Verify public TLS
and anonymous health before authenticated external tests. Record external blockers.

### Task 3: Rollback and automation readiness

After Task 1 passes, exercise a reversible Helm configuration revision/rollback
in hml with identical images and verify the same delivered OS persists.
Inspect existing GitHub CI and registry access without reactivating AWS workflows.
Prepare a concrete additive OCI automation plan based on observed repository state.

### Task 4: Acceptance business telemetry without changing AWS

Own deploy/oci/business-telemetry.py, deploy/oci/test_business_telemetry.py,
deploy/oci/install-business-telemetry.sh and deploy/oci/systemd/oficina-business-telemetry.*.
Create an OCI-only root systemd oneshot + timer every five minutes to aggregate
Neon OS data for hml/prod and send New Relic Event API custom events. Use Python
standard library and psql installed from Oracle Linux package repositories by
installer. Secrets from /etc/oficina/ENV/AUTH_DB_CONFIG and
/etc/oficina/newrelic/licenseKey only at runtime, no secret argv or output.
PostgreSQL subprocess environment PGHOST/PGUSER/PGPASSWORD/PGDATABASE, TLS
verify-full with system CA and PGOPTIONS default_transaction_read_only=on,
statement_timeout bounded. No SQL writes. Both schemas have ordens_servico with
criado_em, diagnosticado_em, orcamento_enviado_em, aprovado_em,
execucao_iniciada_em, finalizada_em, entregue_em, status. Inspect migrations for
exact schema. SQL returns ONLY aggregates, never identifiers/customer data.
Send OficinaBusinessDaily events for each business day of the last seven days
(America/Sao_Paulo), including zero-OS days. Fields: environment, businessDate
(YYYY-MM-DD), createdCount, finalizedCount, deliveredCount, snapshotVersion=1,
timestamp (collection time). Counts group respective event timestamps by day.
Send OficinaBusinessStageSnapshot events per environment for DIAGNOSIS, APPROVAL,
EXECUTION, FINALIZATION over the last seven days by stage completion time.
Fields: environment, stage, sampleCount, totalDurationMs, averageDurationMs only
when sampleCount>0, windowDays=7, snapshotVersion=1, timestamp. Define DIAGNOSIS
criado_em to diagnosticado_em, APPROVAL orcamento_enviado_em to aprovado_em,
EXECUTION execucao_iniciada_em to finalizada_em, FINALIZATION finalizada_em to
entregue_em. Exclude null/negative pairs and count samples correctly.
Send OficinaTelemetryHeartbeat event after successful aggregation and delivery,
environment, healthy=1, timestamp. Do not claim zero business failures; existing
WorkOrderProcessingFailures logs remain the failure source.
US Event API https://insights-collector.newrelic.com/v1/accounts/8439293/events,
API-Key header, gzip optional. HTTP/timeouts fail nonzero with sanitized output.
Persist no credentials locally besides existing root-only files. No fake events:
only aggregate actual DB rows. Repeated snapshots deliberately use latest() in
dashboards rather than sum() so polling cannot inflate counts.
Focused unit tests cover empty-day results, durations and exclusion, event shape,
failure sanitization. Document dependency/runtime usage in report. Installer must
not echo credentials, run with root, use 0700/0600 appropriate protected files,
keep service code root-owned, and limit timer resources. No edits outside ownership;
controller handles deployment and dashboard JSON. Commit owned files only.
