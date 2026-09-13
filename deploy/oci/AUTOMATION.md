# OCI automation readiness

Observed 2026-09-12 (America/Sao_Paulo): GitHub exposes active `app` and `auth`
quality workflows. Existing local deploy.yml uses AWS OIDC, ECR and AWS resources;
it must remain unchanged and must not be invoked for this OCI deployment.
Current ARM64 images are imported into the single VM containerd, not published
to a registry. The working OCI deployment is operated through SSH and Helm.

## Additive delivery plan

1. Add a separate manual OCI workflow, initially without push triggers. Run
   existing quality checks plus OCI chart and telemetry tests.
2. Build app and auth for linux/arm64, publish to a dedicated GHCR package with
   least-privilege workflow `packages: write`, and attest immutable digests.
   Confirm registry visibility and pull access before adopting registry images;
   GitHub API access alone does not establish package pull permissions.
3. Use a dedicated deployment identity with a restricted remote deploy command.
   Do not copy the user's opc administrator private key into GitHub secrets.
   Keep runtime database, signing and New Relic credentials only on the VM.
4. Deploy hml by digest, await migrations and readiness, run business smoke and
   HTTPS checks, then record deployment digest and evidence as an artifact.
5. Promote exactly those digests to prod with an environment approval gate.
   Validate read-only health/auth isolation and do not create business fixtures.
6. Roll back application/configuration through Helm while preserving database
   migrations and data. Treat destructive schema changes as a separate design.

This plan is not evidence of an executed OCI CI/CD pipeline. No new registry
credentials, GitHub deployment identity, or AWS workflow execution was created.
