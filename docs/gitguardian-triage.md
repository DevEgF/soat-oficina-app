# GitGuardian triage - 2026-09-10

PR #4 findings were reviewed against the reported commits and current runtime configuration.

- Incidents 29342119, 29345044, 29345045 and 29345046: development/test credentials used in local PostgreSQL, CI, local profiles and test fixtures. Classified as `test_credential` through the GitGuardian API.
- Incident 37137231: the detector paired a username with a role name passed to `credentials.passwordFor(...)`. That call retrieves an externally configured password; the argument is not a password. Classified as `false_positive`.

`StaffCredentials` rejects username-equivalent defaults outside exclusively `local`/`test` profiles. Runtime deployment credentials are supplied externally. No scanner was disabled, no path-wide exclusion was added, and commit history was preserved. Existing findings in other incident IDs were not modified.

This record requests a fresh PR scan after triage; merging remains conditional on successful checks.
