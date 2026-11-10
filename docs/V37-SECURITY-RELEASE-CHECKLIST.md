# V37 security and validation checklist (review required)

## Already staged in this branch
- Background learned-filter promotion is locked at the coordinator call site.
- LearningEngine.runCycle defaults to promotionLocked=true for other callers.
- Shadow research and automatic rollback remain enabled. Existing PROMOTED ledger events are not rewritten.

## Before enabling owner approval
- Implement a durable, authenticated, explicit owner approval event bound to candidate ID, exact rule version, evidence snapshot hash, engine version and expiry.
- Separate candidate APPROVED from PROMOTED; reject stale approvals, changed evidence and health-gate failures.
- Add replay tests for restart, duplicate approvals, revoked approvals, rollback and rejected promotions.
- Do not allow the UI to bypass the source/health gates. Never silently approve.

## Public signing key (HIGH PRIORITY, not fixed by this branch)
- Public debug.keystore.base64 and known Android debug credentials must be treated as compromised for app-update trust.
- Simply deleting the file or moving the same key to GitHub Secrets does not rotate it, and does not remove it from Git history.
- Do not change the installed application's signing certificate or package ID without testing an explicit migration/backup plan for user data and tokens.
- Use a new privately generated production signing key, stored outside source control, for future production distribution. Use GitHub Actions secrets only with appropriate protections and no secret logging.
- Verify Android package-signature compatibility and migration on a spare test device before any public release.
- Warn users not to sideload untrusted APKs. GitHub Actions provenance alone is not a cryptographic guarantee if the signing key is public.

## Prediction validation before release
- Resolve all known-open forensic defects rather than treating expected defect counts as success.
- Verify 53 JVM tests in CI, add Android instrumentation tests and immutable ledger replay fixtures.
- Validate unseen, non-overlapping forward predictions with actual broker quotes and transaction costs.
- Report calibrated probability, WAIT coverage, confidence intervals and drawdown; no guaranteed win-rate claims.

No secrets, credentials or signing material belong in this document.
