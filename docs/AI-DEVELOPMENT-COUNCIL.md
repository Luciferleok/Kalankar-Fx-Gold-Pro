# KALANKAR FX GOLD PRO — AI Development Council (V37)

## Operating rules
1. **Owner request:** capture scope, acceptance criteria, preservation of existing ledgers/API-key settings, and rollback plan.
2. **Current-state auditor:** fetch latest `main` SHA and open PR diff; never assume a previous chat's SHA is still current.
3. **Independent reviewers:** architecture, market-data integrity, Android security, and test engineer independently evaluate the proposal. ChatGPT, Gemini, or Claude opinions count as *actual* independent reviews only when their APIs or human-provided outputs were actually invoked; do not invent a discussion.
4. **Evidence arbiter:** record disagreements with file/line evidence and tests. No majority-vote substitute for proof.
5. **Implementation:** branch from latest `main`; one scoped change per PR. No secrets in code, logs, artifacts or prompts.
6. **Gates:** JVM regressions and Android `assembleDebug` must pass on PR. Android device testing, ledger migration validation, signing security, and real-market forward validation remain separate required gates before a production release.
7. **Release:** human owner reviews results and explicitly authorizes merge/release. No automatic main merge or installation. A green debug APK does not certify forecast accuracy or financial returns.

## PR acceptance checklist
- [ ] Base SHA and head SHA documented; no stale patch overwrite
- [ ] Independent code/security review with explicit disagreements
- [ ] Tests added or updated; known forensic defects not concealed by the expected-output list
- [ ] PR CI green (JVM + Android compile)
- [ ] Ledger/data migration and API-key behavior reviewed
- [ ] No public debug signing key treated as production-safe
- [ ] Owner authorizes merge and separately authorizes release

## Known immediate issues
- Previous draft PR #1 was based on older `main` and must be reconciled with V37-A.2 rather than blindly merged.
- Current `tools/jvm-tests/run.sh` intentionally accepts known forensic defect reports. A green CI result does not mean all prediction defects are fixed.
- No verified Gemini/Claude multi-agent API integration yet.
- No production signing migration or verified Android device test yet.
- The workflow added by this PR checks PRs; branch protection requiring the checks must be configured in GitHub repository settings by an authorized administrator.
