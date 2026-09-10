# Application chart

AWS prerequisites: foundation bootstrap must create the `hml`/`prod` namespaces, stable `oficina-app` service accounts, and scoped SecretProviderClass deployment RBAC before the first Helm install. The chart intentionally does not own these shared prerequisites. EKS Pod Identity uses namespace + service-account name. Configure metadata values `image.repository`, immutable `image.digest`, `db.endpoint`, `db.secretArn`, `jwt.secretArn`, and `staff.secretArn`; never put credential values in Helm values.

The ConfigMap (-30) and SecretProviderClass (-20) are retained revision-qualified pre-install/pre-upgrade hooks. The migration Job (-10) uses those exact resources and exits after Flyway/context startup. Runtime pods use the same revision resources and disable Flyway. No migration runs on rollback. The ConfigMap and CSI aliases are shared by both paths. AWS uses verify-full TLS with the image's RDS CA bundle; local kind disables CSI/ADOT and uses a synthetic database and explicit local profile.

Helm rollback restores Kubernetes manifests, not database changes. All Flyway migrations must remain backward-compatible with the previous admitted application release. Apply additive migrations first; destructive schema changes require a separate reviewed migration after old releases are retired. Existing migration scripts contain no destructive drop/rename.

Hook ConfigMaps/SPCs are not deleted automatically by Helm uninstall. Delivery scripts must retain dependencies for admitted rollback revisions and explicitly delete release-labeled hook resources on uninstall. Successful migration Jobs delete automatically; failed Jobs expire after 24 hours. Do not garbage-collect the dependencies of a running or admitted rollback revision.

Validate without AWS:

```powershell
python -m pip install PyYAML
python deploy/helm/oficina/tests/render.py
helm lint deploy/helm/oficina --namespace hml -f deploy/helm/oficina/tests/fixture-values.yaml
```

The fixture contains synthetic resource identifiers only. `scripts/kind-values.yaml` selects local mode; create PostgreSQL before installation and load the `oficina:kind-test` image into kind.

References: [Helm hook lifecycle](https://helm.sh/docs/topics/charts_hooks/) and [RDS TLS certificates](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).

## Optional synthetic acceptance data

`demoFixtures.enabled=false` is the default. Set GitHub Environment variable `ENABLE_DEMO_FIXTURES=true` only for the academic acceptance dataset. The post-install/post-upgrade Job inserts three synthetic customers and three owned orders after migrations; it never resets existing records or overwrites a decision. SQL is transactional and repeated execution is idempotent. A conflict with unrelated identifiers fails the deployment rather than overwriting data.

Runtime PostgreSQL credentials come from the same CSI files inside the Job. The agent/workflows do not retrieve their values. The fixture Job uses the same immutable, scanned application image, which includes psql and the public RDS CA bundle; psql verifies TLS in AWS. Local kind uses synthetic credentials and tests this optional hook.

Synthetic fixture variables for auth smoke: `SMOKE_ACTIVE_CPF=52998224725`, `SMOKE_BLOCKED_CPF=11144477735`, `SMOKE_OTHER_ACTIVE_CPF=12345678909`, `SMOKE_UNKNOWN_CPF=98765432100`, `SMOKE_TRACKING_CODE=00000000-0000-4000-8000-000000001001`. These are test-only values, not real customer records. Postman uses the same first tracking code/workOrderId, approval code ending `1002`, rejection code ending `1003`, and an ADMIN or MASTER staff session provided at runtime.
