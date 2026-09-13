# OCI K3s continuation

User authorized Oracle Linux 9 ARM64 (2 OCPUs, 12 GB), Neon PostgreSQL in Ohio,
New Relic, and preservation of the AWS implementation on 2026-09-12. User also
authorized protected files on the VM and Kubernetes Secrets instead of AWS
Secrets Manager for this deployment. Secret values must never enter Git or chat.

## Boundaries

- Add separate OCI files; do not edit or enable existing AWS workflows, Terraform,
  Lambda handlers, or the AWS application Helm chart.
- K3s single server on the existing VM. Keep SELinux enforcing and firewalld active.
  Expose only application HTTP/HTTPS on the host; administer Kubernetes via SSH.
  Keep kubeconfig root-only and enable Kubernetes secret encryption at rest.
- Namespaces hml/prod; independent JWT/staff secrets; Neon direct TLS connections
  and schema selection. Direct endpoints avoid transaction-pool search_path issues.
- Application uses an independent Helm chart with an ordered migration hook,
  existing Kubernetes Secrets, non-root containers, probes, and bounded HPA.
- ARM64 runtime image built from the same application source. Promotion must reuse
  a digest, not rebuild for production. No reuse of an unverified AMD64 digest.
- Auth may gain a separate HTTP adapter sharing the existing use case and JWT
  implementation. This adapter is not serverless and does not replace the AWS
  Lambda evidence for that acceptance criterion.
- New Relic configuration is additive, with existing-secret references. Installation
  and telemetry verification require the account region and ingestion key.

## Validation and external dependencies

Check K3s Ready, CoreDNS, metrics-server and ingress readiness; render and lint Helm;
test fail-closed configuration; verify real DB TLS/migration and security flows
only once credentials exist. No configured credentials were found in the standard
VM directories during initial inspection. Public TLS/domain selection and external
OCI security-list reachability must be verified before sending authentication data.

Single-node K3s provides restart/replica management, not node-level HA. This
continuation does not by itself close the outstanding business-metric or video
acceptance gaps. Actual progress and blockers belong in deploy/oci/README.md.
