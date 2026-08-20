# FrameFlow Select — FINAL HANDOVER (S8)

**Scope of this document:** a truthful, reality-checked handover for the **local
MVP** delivered in this repository. It makes **no** market, production-readiness,
legal-compliance, or performance claims. All external validation remains pending
(`EXTERNAL_VALIDATION_PENDING` — see the final section).

---

## 1. Summary

FrameFlow Select is a **local portfolio MVP** for teams producing AI-generated
short-form vertical video at volume. It covers the ingest-to-selection loop:

```text
Project / Prompt / Brief / Quality Profile
  → Generation Batch → Candidate Videos
  → Deterministic QA → Evidence-grounded AI QA
  → Duplicate Clustering → Ranking / Top-K → Human Review → Exported Selection Set
```

Delivered locally in **S0–S6** (Java modular monolith + Python AI worker + synthetic
fixtures). The controlled pivot from legacy FrameFlow collaboration is preserved and
recoverable; the six authoritative books and machine contracts are installed; all
S0–S6 stage gates were Owner-Proxy accepted with independent verification.

**Important frame:** everything here is demonstrated on **synthetic, locally generated
fixtures** and a **deterministic Fake semantic provider**. It is *not* proof of
real-world accuracy, market fit, or production readiness.

---

## 2. Architecture

### 2.1 Java modular monolith (`frameflow-app` + `frameflow-modules`)
A single deployable Spring Boot app composed of modules with enforced boundaries
(via ArchUnit `ModuleArchitectureTest`):

- **`frameflow-modules/identity`** — users, teams, roles
  (OWNER/OPERATOR/REVIEWER/VIEWER), JWT authN/authZ, refresh tokens, idempotency,
  active-member policy. Hexagonal (application/domain/infrastructure
  ports-and-adapters).
- **`frameflow-modules/product`** — projects, quality profiles (versioned),
  batches, candidates, analysis runs/findings, duplicate clustering, ranking,
  top-K selection and export. Hexagonal with a **storage port** and a **worker
  dispatch port**.
- **`frameflow-shared-kernel`** — shared minimal contracts.
- **`frameflow-app`** — bootstraps the modules, Flyway migrations (V1–V8,
  forward-only), actuator health/readiness, OpenAPI contract surface.

### 2.2 Python AI worker (`frameflow-ai-worker`)
A CLI/codebase that is decoupled from Java business tables (it never owns them):

- Deterministic QA via `ffprobe` facts (aspect, duration, resolution, audio,
  black/freeze detectors) — `detectors.py`, `media.py`, `quality.py`.
- Duplicate fingerprinting/clustering via perceptual hashes — `duplicates.py`.
- Semantic provider SPI + deterministic **FakeSemanticProvider** (default);
  optional OpenAI-compatible adapter present but disabled without a key —
  `semantic.py`.
- Urls: standardized result/decision/finding JSON consumed by the Java side —
  `pipeline.py`.
- Synthetic fixture generator (`fixtures.py`) and evaluation harness
  (`eval.py`), with bundled FFmpeg via `static-ffmpeg`.

### 2.3 Services
- Projects, quality profiles, batches, candidates (product).
- Batch orchestration (`BatchOrchestrator`, S4): bounded sequential processing,
  progress, partial-failure reconciliation, rerun — deterministic quality gate.
- Semantic QA ingestion (S5): evidence-grounded findings, UNKNOWN/FAILURE paths.
- Selection/ranking (S6): deterministic weighted ranking, sha256-based duplicate
  clusters, top-K lock (immutable) + human override, CSV/JSON export.

### 2.4 Storage port & worker dispatch
- **Storage port** (`StoragePort` → `LocalStorage`): local filesystem media root
  (default `data/media`, configurable via `frameflow.storage.root`).
- **Worker dispatch** (`WorkerDispatcher` → `LocalWorkerDispatcher` async
  best-effort enqueue; `WorkerRunner` sync invocation in the S3/S4 vertical slice)
  runs `python -m frameflow_ai.cli analyze <media> <profile>`, wired via
  `frameflow.worker.python` / `frameflow.worker.cli`. Unset ⇒ `WORKER_NOT_CONFIGURED`
  (expected, recoverable).

### 2.5 State machines
- **Candidate lifecycle:** UPLOADING → STORED → READY_FOR_ANALYSIS → ANALYZING →
  ANALYZED / ANALYSIS_ERROR / INVALID / UPLOAD_FAILED.
- **Batch lifecycle:** PROCESSING → REVIEW_READY | PARTIAL_FAILURE.
- **Analysis run:** STARTED → COMPLETED / FAILED (idempotent result ingestion).
- **Selection:** candidate decisions (ELIGIBLE/INELIGIBLE) + human overrides;
  top-K LOCKED immutability.

---

## 3. What is covered by tests / evidence

All evidence under `evidence/frameflow-select/**`:

- `s0/` — pivot baseline hygiene, roles, gate JSON.
- `s1/` — synthetic evaluation harness + feasibility verdict (eval 10/10).
- `s2/` — domain foundation + verdict.
- `s3/` — single-candidate vertical slice + E2E verdict.
- `s5/` — evidence-grounded semantic QA (Fake provider, optional adapter).
- `s6/` — duplicate clustering, ranking, top-K selection.
- S4 batch engine is covered by `BatchEngineIntegrationTest` (part of the Java
  suite) and recorded in the S4 gate commit.

Test counts (see `evidence/final/test-summary.json`):

- **Java:** 35 tests / 25 classes — green (identity authN/Z contracts, migration,
  OpenAPI diff, product flow, single-candidate E2E, batch engine, module
  architecture). Requires Docker (Testcontainers).
- **Python:** 20 tests — green (detectors, media, quality, duplicates, semantic).
- **Synthetic eval:** 10/10 matched expected on the generated dataset.

---

## 4. How to run + verify

Full local guidance is in **`RUNBOOK.md`**. Short form:

```bash
docker compose -f docker-compose.local.yml up -d          # PostgreSQL
./mvnw -B clean verify                                     # Java build + tests
cd frameflow-ai-worker && PYTHONPATH=src .venv/bin/python -m pytest
cd frameflow-ai-worker && PYTHONPATH=src .venv/bin/python -m frameflow_ai.fixtures --output ../experiments/fixtures/generated
cd frameflow-ai-worker && PYTHONPATH=src .venv/bin/python -m frameflow_ai.eval --dataset ../experiments/fixtures/generated --profile ../experiments/fixtures/profile-ECOMMERCE_SHORT_AD_V1.json
```

---

## 5. Known limitations

```text
- Web frontend (S7, frameflow-web) is NOT implemented in this delivery: the directory
  contains only package.json and no Next.js source. The RUNBOOK 'npm run build' step is
  therefore not runnable until S7 is implemented; it is recorded in evidence/final as
  not-applicable/not-run, not as a passing gate.
- Semantic QA uses the deterministic Fake provider by default. Real OpenAI-compatible
  provider is optional and disabled without a key (never required locally).
- Validation reports possible_secret WARNINGS that become ERRORS only in the 'final'
  phase, all heuristic false positives (see evidence/final/known-limitations.md and the
  validator note). They are NOT real secrets; the authoritative pack DECISION-POLICY file
  and the validator are intentionally left unchanged.
- All results derive from synthetic fixtures; no real customer media, real provider
  metrics, or market validation.
- M02–M17 / microservices / Kafka / K8s / Istio / GPU / auto-publish are explicitly
  out of scope for the local MVP.
```

---

## 6. External validation pending

The following are **explicitly NOT claimed** and remain external:
`EXTERNAL_VALIDATION_PENDING`:

- Real customer pilot(s).
- Paid model API key / real provider accuracy and cost.
- Market validation / revenue / fit.
- Production cloud deployment, HA/DR, SOC2-type certification,
  GPU infrastructure, automatic publishing.

The local MVP is complete; the above are the next, externally-validated steps.
