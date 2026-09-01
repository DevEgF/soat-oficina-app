# Phase 3 local toolchain

- Observed on: 2026-09-01
- Gate: `pwsh -NoProfile -File scripts/test-prerequisites.ps1`
- Result: passed

## Tool versions

| Tool | Version |
|---|---:|
| Git | 2.54.0 |
| GitHub CLI | 2.98.0 |
| AWS CLI | 2.36.35 |
| Terraform | 1.16.0 |
| TFLint | 0.64.0 |
| Checkov | 3.3.16 |
| kubectl | 1.36.1 |
| Helm | 4.1.4 |
| Docker CLI | 29.6.2 |
| Node.js | 24.16.0 |
| Java | 21.0.11 |
| Python | 3.14.5 |
| PowerShell | 7.6.5 |

## Operational checks

| Check | Result |
|---|---|
| Docker Engine | Responding |
| GitHub authentication | Authenticated as `DevEgF`, matching the current remote owner |
| AWS identity | Profile `oficina-admin` confirmed by STS |
| AWS region | `us-east-1` |

No credential, token, password, access key, secret value, or machine-specific path is stored in this inventory.
