# FrameFlow Select — KNOWN LIMITATIONS (S8 final)

Reality-checked list of limitations for the local MVP. None of these were papered
over: the code, tests, contracts and thresholds are **unchanged and unweakened**.

---

## 1. Web frontend (S7) — implemented and building (desktop local)

- `frameflow-web/` is a full Next.js App Router + React + TypeScript + Tailwind app
  (pages: /, /login, /dashboard, /projects/[id], /batches/[batchId],
  /batches/[batchId]/selection, /candidates/[id] with video review player), committed
  on frameflow-select/main.
- `cd frameflow-web && npm run build` **passes** (8 routes type-checked + compiled);
  recorded in `evidence/final/test-summary.json` as PASS.
- Limits: token handling uses client-side storage with a BFF/HttpOnly note in the web
  README; it is a desktop-first local UI and has not been browser-E2E-tested against a
  live backend in this delivery (the backend API contract is verified separately).

## 2. Semantic QA uses the deterministic Fake provider by default

- `FakeSemanticProvider` produces `PASS/VIOLATE/UNKNOWN/ERROR` verdicts without any
  external secret and drives all E2E/eval. This is correct for a **local, offline,
  reproducible** MVP, but it is **not** real multimodal accuracy.
- The optional OpenAI-compatible adapter (`OptionalOpenAiCompatibleProvider`) is
  present but **disabled without a key**; it is never required locally.
- All observed `matchedExpected 10/10` figures are against synthetic fixtures with
  the Fake provider — **not** a claim of real-world accuracy.

## 3. `validate_repository --phase final` possible_secret false positives

The validator flags three tracked files as `possible_secret`. In the **`final`**
phase these are promoted to **ERRORS** (in earlier phases they are warnings). All
three are **heuristic false positives** — verified, not genuine secrets:

| File | Why flagged | Why false positive |
| --- | --- | --- |
| `.frameflow/decision-policy.yaml` | line 52 `missing_api_key: use_fake_provider_and_continue` matches the `api_key: <24+ alnum>` pattern | Authoritative policy text telling the agent to *use the fake provider* when a key is missing — not a key. |
| `FRAMEFLOW_SELECT_AUTOPILOT/execution/DECISION-POLICY.yaml` | same `missing_api_key:` line | Same authoritative pack file (must NOT be altered). |
| `evidence/frameflow-select/s2/s2-domain-verdict.txt` | line 65/66 contain the literal string `"possible_secret:.frameflow/decision-policy.yaml"` | It is an S2 evidence record that *quotes the same false-positive warning*. It is evidence, not a secret. |

**Handling (per the delivery contract):** the authoritative pack DECISION-POLICY.yaml is
left **unchanged**; the validator is left **unchanged** (no weakening); the S2 evidence is
left unchanged. These are documented here and treated as known warnings/false positives.
Because the final phase promotes them to errors, the validator cannot report
`ok:true` purely on this account, but the items are false positives, not defects.

## 4. Synthetic-only, no external validation

- All media are locally generated synthetic fixtures (10 cases); no real customer
  media was used.
- No real provider API key; no real provider metrics/cost; no customer pilot.
- No market validation, no production cloud deployment, no legal/compliance/GDPR-type
  certification, no GPU infrastructure, no automatic publishing.
- External status is `EXTERNAL_VALIDATION_PENDING` by design.

## 5. Out-of-scope architectural boundaries (not implemented)

- Microservices split, Kafka, Kubernetes, Istio, generic agent orchestration, video
  generation, automatic publishing, complex billing, mobile — all explicitly out of
  scope for the local MVP and listed as non-goals in the authoritative books.

## 6. Operational notes

- The Java suite requires Docker (Testcontainers `postgres:16-alpine`); without
  Docker the DB-backed tests cannot run.
- Worker dispatch is a thin local port; when `frameflow.worker.python` /
  `frameflow.worker.cli` are unset the pipeline is fully testable but reports
  `WORKER_NOT_CONFIGURED` for real (non-mocked) worker runs — expected and
  recoverable, not a failure.
