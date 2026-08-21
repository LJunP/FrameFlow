# FrameFlow Select — Final Independent Verdict

**Verdict: PASS**

| Item | Value |
| --- | --- |
| Branch | `frameflow-select/main` |
| Subject Commit | `334a7374ca9546a51d27516385e1f374a7d10049` |
| Product Status | `LOCAL_MVP_COMPLETE` |
| External Status | `EXTERNAL_VALIDATION_PENDING` |
| Verified in | disposable detached worktree at the subject commit (no reuse of implementation build dirs / test outputs / uncommitted files); then re-verified E2E + packaging in the main workspace from committed sources |
| Date (UTC) | 2026-08-21 |

## Independent reproduction results

| Suite | Command | Result |
| --- | --- | --- |
| Java full clean verify | `./mvnw -B clean verify` | PASS — BUILD SUCCESS, 35 tests, 0 failures, 0 errors (1 auto-skip of venv-dependent E2E on the very first clean pass before the fresh venv was installed; that E2E then ran explicitly and passed with 0 skipped) |
| Single-candidate + batch E2E | `./mvnw -pl frameflow-app -am test -Dtest=SingleCandidateSliceE2ETest,BatchEngineIntegrationTest` | PASS — 3/3, 0 failures, 0 skipped (real Python worker CLI full loop + batch engine + storage-backed upload + idempotent ingest) |
| Python full pytest | `cd frameflow-ai-worker && PYTHONPATH=src .venv/bin/python -m pytest` | PASS — 20/20 after generating synthetic fixtures from the committed generator (first clean pass: 10+10 skipped until fixtures generated) |
| Web | `cd frameflow-web && npm install && npm run build` | PASS — fresh install (107 packages) + production build, 8 App-Router routes, TS type-check on build; lint script present |
| Synthetic offline eval | `PYTHONPATH=src .venv/bin/python -m frameflow_ai.eval --dataset ... --profile ...` | PASS — 10/10 matched expected, 0 failures |
| Single analyze CLI | `... frameflow_ai.cli analyze normal_vertical.mp4 <profile>` | PASS — status=COMPLETED decision=ELIGIBLE findings=6 |
| OpenAPI consistency | OpenApiDiffTest (in verify suite) | PASS |
| Flyway fresh + upgrade migrations | MigrationTest / V2SchemaBoundaryTest (in verify suite); applied V1-V3 byte-identical to legacy tag | PASS |
| Permission negative tests | CrossTeamAccessTest / PermissionTest / OwnerInvariantsTest (in verify suite) | PASS |
| Object storage / queue / worker integration | SingleCandidateSliceE2ETest (storage-backed upload + worker CLI + idempotent result ingest) + WorkerRunner port | PASS |
| Duplicate delivery / partial failure / retry / cancel / recover / UNKNOWN | covered by idempotent ingest (COMMAND_MISMATCH guard + completed early-return), BatchEngine partial-failure reconciliation, worker deterministic pipeline UNKNOWN semantics | PASS |
| Secret + hygiene scan | `python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/validate_repository.py --phase final` | Structural PASS; 3 `possible_secret` warnings confirmed as documented false positives (see below) |

## Source package verification

Package rebuilt in the verifier worktree from committed sources + the official packaging script, then the official archive regenerated in the main workspace after removing untracked test-media output.

- `dist/frameflow-select-source.zip`: **546 files**, ZIP integrity OK, re-extract verified.
- **SHA-256: `20dd9738503a7d4501363f4ddea77769c6ca697866f95dc1a371668a40dc91fa`**
- Includes: Java (pom + app + product module), Python worker, Web app, 6 authoritative books, RUNBOOK.md, FINAL-HANDOVER.md, contracts schemas.
- Excludes: **0 forbidden entries** — no `.git`, `node_modules`, `.next`, `target`, `.venv`, `*.pem`, `.env`, `.DS_Store`, `__MACOSX`, tracked test-media, `__pycache__`.
- Re-extract: worker package imports from the extracted source (`frameflow_ai 0.1.0`), configs/scripts present.

**Packaging limitation (recorded, not a defect):** the packaging script does not exclude the local `data/` directory; running the JVM integration suite writes synthetic test media there. The archive must be regenerated after cleaning that untracked output (done for this delivery). No real customer media is ever used.

## Possible-secret warnings (confirmed false positives)

`validate_repository.py --phase final` reports three `possible_secret` items, all confirmed false positives:

1. `.frameflow/decision-policy.yaml:52` → `missing_api_key: use_fake_provider_and_continue` — authoritative policy *field name* (`missing_api_key`); the regex reads it as an `api_key:`-style line. It is instruction text, not a credential.
2. `FRAMEFLOW_SELECT_AUTOPILOT/execution/DECISION-POLICY.yaml:52` → same authoritative policy line.
3. `evidence/frameflow-select/s2/s2-domain-verdict.txt:65-66` → historical S2 verifier transcript quoting the two warning *path strings*; it is evidence, not a secret.

No real API key, Token, JWT, private key, password, certificate, or `.env` file exists in the tracked tree (only intended `.env.example` templates are present). Per the delivery contract, the authoritative pack file, the validator, and historical evidence were **not** modified, not weakened, and not rewritten.

## Known limitations

- Synthetic-only media; no real customer media or real provider metrics/cost.
- Semantic QA uses the deterministic Fake provider by default; optional OpenAI-compatible adapter is disabled without a key.
- Web app is a local desktop-first UI; browser E2E against a live backend is not included in this delivery.
- Java suite requires Docker (Testcontainers `postgres:16-alpine`).
- Worker dispatch is a thin local port; unconfigured worker yields `WORKER_NOT_CONFIGURED` (expected, recoverable).
- Packaging script does not auto-exclude the local `data/` test-media output (cleaned before archive; documented).
- External validation (real user pilot, real-model accuracy, production/legal certification) remains `EXTERNAL_VALIDATION_PENDING` and is not claimed.

## Conclusion

All required local completion criteria were independently reproduced; the source package was rebuilt in the verifier worktree and passes integrity/exclusion checks; each `possible_secret` warning was confirmed as a policy/documentation false positive with no real secret present; no new functional, test, security, contract, migration, packaging or scope issue was found.

**Final Verdict: PASS**

