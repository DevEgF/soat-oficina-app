# OCI acceptance continuation results

Date: 2026-09-12 America/Sao_Paulo (runtime UTC crosses into September 13).

## Verified live

- OCI security list now permits stateful TCP 80 and 443; public HTTP reached
  Traefik. Kubernetes administrative ports were not added.
- cert-manager v1.20.3 installed from the official OCI Helm chart; Let's Encrypt
  certificates Ready in hml and prod, with automated renewal.
- Public HTTPS health returned UP with normal certificate verification for
  https://hml.129.213.121.122.sslip.io/actuator/health and
  https://oficina.129.213.121.122.sslip.io/actuator/health.
- Full synthetic hml journey passed: owner and second customer authentication,
  denial of second-customer access/approval, diagnosis, quotation, approval,
  execution, finalization and delivery. Retained evidence OS:
  ca8e7f6e-58ca-4c19-941e-051bdb30e901. Its synthetic customers, vehicle and service
  are intentionally retained. No production business writes were made.
- hml Helm revision 2 -> 3 changed only CPU request with identical images;
  rollback produced revision 4 restoring revision 2. The delivered OS was verified
  before, after upgrade, and after rollback. This proves configuration rollback,
  not rollback between different application binaries.
- Business collector installed as root systemd timer every five minutes. After
  upgrading the Oracle repository PostgreSQL client from 13 to 16.14, systemd
  reported Result=success and ExecMainStatus=0. Queries select hml/prod schemas
  explicitly, use verified TLS and read-only mode, and emit aggregates only.
- Existing New Relic dashboards were found and preserved rather than duplicated:
  HML https://one.newrelic.com/dashboards/detail/ODQzOTI5M3xWSVp8REFTSEJPQVJEfGRhOjEzMTY1MzA5?account=8439293
  PROD https://one.newrelic.com/dashboards/detail/ODQzOTI5M3xWSVp8REFTSEJPQVJEfGRhOjEzMTY1MzEw?account=8439293
  Both separate business, API/auth and Kubernetes indicators. HML API charts
  visibly rendered live request and latency data. Final UI validation showed
  hml with 2 created/finalized/delivered OS and 2 samples in all four stages;
  prod showed seven explicit zero-count days and zero stage samples. Both
  heartbeats showed healthy=1. Prod's average chart filters sampleCount > 0
  to avoid presenting missing durations as zero.
- Final runtime check: app/auth deployments 1/1 in both environments, certificate
  Ready=True in both, timer active, collector successful, HTTP redirect 301.
  Node measured 99m CPU (4%) and 2634Mi memory (24%).
- Local final suite: 18 tests, 17 passed and 1 PostgreSQL integration test skipped
  in the controller invocation. Worker separately exercised the integration test
  on a fresh labeled PostgreSQL 16 fixture; final independent review approved.

## Limits of acceptance evidence

AWS deployment implementation is preserved; no AWS operations were performed.
OCI auth is a container, not AWS Lambda. A requirement specifically demanding
AWS services/serverless cannot be represented as satisfied by this adaptation.
OCI CI/CD and registry publication are not executed; see deploy/oci/AUTOMATION.md.
Alert firing and notification delivery, final narrated video and delivery PDF
are not established by these deployment/dashboard checks. Do not mark the entire
academic activity accepted solely because this runtime continuation passed.

## Reproducibility

See deploy/oci/install-https.sh, business-smoke-vm.py, verify-rollback.sh,
install-business-telemetry.sh and generate-dashboards.py. Generated dashboard
JSONs are portable templates; existing live dashboard IDs are listed above.
Secrets remain in protected VM files and Kubernetes Secrets, never in this report.
