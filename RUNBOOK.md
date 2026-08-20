# FrameFlow Select — RUNBOOK

Local runbook for the **FrameFlow Select** local MVP (S0–S6: Java modular monolith +
Python AI worker + synthetic fixtures). All values are **local-only** example values;
there are no real secrets anywhere in this repository.

---

## 1. Quick start (local)

### Prerequisites

- Java 17 (JDK), a shell, and `git`
- Docker (required by the Java test suite via Testcontainers, and for the local PostgreSQL)
- Node 18+ / npm (only if/when the web frontend is implemented — see [Known limitations](#known-limitations))
- No system FFmpeg is required: the Python worker bundles FFmpeg through `static-ffmpeg`

### 1a. Start the database (PostgreSQL)

```bash
docker compose -f docker-compose.local.yml up -d
```

This exposes PostgreSQL on `127.0.0.1:54329`. The app connects with the
**defaults from `frameflow-app/src/main/resources/application.yml`** — you only
need to override them if your environment differs:

| Property | Default |
| --- | --- |
| `FRAMEFLOW_DB_URL` | `jdbc:postgresql://127.0.0.1:54329/frameflow` |
| `FRAMEFLOW_DB_USERNAME` | `frameflow` |
| `FRAMEFLOW_DB_PASSWORD` | `frameflow_local_only` |
| `FRAMEFLOW_SERVER_ADDRESS` | `127.0.0.1` |
| `FRAMEFLOW_SERVER_PORT` | `8080` |

A ready-to-copy example lives in `.env.example`.

### 1b. Start the Java backend

**Option A — via Maven (development)**, from the repository root:

```bash
./mvnw spring-boot:run
```

**Option B — packaged jar**, from the repository root:

```bash
./mvnw -B -q -DskipTests package
java -jar frameflow-app/target/frameflow-app-0.1.0-SNAPSHOT.jar
```

On startup, Flyway applies the forward-only migrations **V1 → V8** automatically.
Health/readiness endpoints are exposed by Spring Actuator
(`/actuator/health`, `/actuator/info`).

### 1c. Start the Python AI worker

The worker is a CLI, not a long-running server. Run a single-file analysis against
a published quality profile:

```bash
cd frameflow-ai-worker
.venv/bin/python -m frameflow_ai.cli analyze <video> <profile>
```

(For example:
`PYTHONPATH=src .venv/bin/python -m frameflow_ai.cli analyze ../experiments/fixtures/generated/normal_vertical.mp4 ../experiments/fixtures/profile-ECOMMERCE_SHORT_AD_V1.json`.)

To wire the Java backend to this worker, point the Spring properties
`frameflow.worker.python` and `frameflow.worker.cli` (e.g.
`-Dframeflow.worker.python=<path>/.venv/bin/python -Dframeflow.worker.cli=frameflow_ai.cli`).
When these are unset the pipeline is still fully testable; a run simply reports
`WORKER_NOT_CONFIGURED` (see [Troubleshooting](#troubleshooting)).

---

## 2. Full test path

Run all three suites from the repository root:

```bash
# 1) Java (build + full test suite; Testcontainers starts PostgreSQL in Docker)
./mvnw -B clean verify

# 2) Python worker unit tests
cd frameflow-ai-worker
PYTHONPATH=src .venv/bin/python -m pytest

# 3) Web frontend build (S7 — see Known limitations; source is not yet implemented)
cd frameflow-web
npm run build
```

> **Note:** the Java suite (including the S3 single-candidate E2E and the S4 batch
> engine integration test) requires **Docker** for Testcontainers. See
> [Docker absent](#docker-absent).

Latest verified counts (see `evidence/final/test-summary.json`):

- Java: 35 tests across 25 test classes — all green
- Python: 20 tests — all green
- Eval on synthetic dataset: 10/10 matched expected

---

## 3. Synthetic data demo

Generate the synthetic fixture dataset (reproducible, copyright-safe, bundled
FFmpeg via `static-ffmpeg`):

```bash
cd frameflow-ai-worker
PYTHONPATH=src .venv/bin/python -m frameflow_ai.fixtures --output ../experiments/fixtures/generated
```

Then run the evaluation harness against a quality profile:

```bash
cd frameflow-ai-worker
PYTHONPATH=src .venv/bin/python -m frameflow_ai.eval \
  --dataset ../experiments/fixtures/generated \
  --profile ../experiments/fixtures/profile-ECOMMERCE_SHORT_AD_V1.json
```

Expected output: `casesTotal: 10, tested: 10, matchedExpected: 10, failures: []`.

---

## 4. Default provider (Fake) and the optional real provider

The Python worker's **default** semantic provider is the deterministic
`FakeSemanticProvider` (`frameflow-fake-semantic-v1`). It reproduces
`PASS / VIOLATE / UNKNOWN / ERROR` verdicts without any external secret, records
a usage ledger, and drives the E2E / evaluation paths.

An **optional** real OpenAI-compatible multimodal adapter
(`OptionalOpenAiCompatibleProvider`) is present but **disabled by default** — it
is active only when an API key is supplied, and is never required locally. Inside
`frameflow-ai-worker` you would set the provider up with a key through the venv
(`.venv`); with no key its `evaluate()` returns `ERROR (provider not configured)`.

---

## 5. Common troubleshooting

### Docker absent
The Java suite uses **Testcontainers** (`postgres:16-alpine`) for every database
test. If Docker is not running, start it first, or run only non-DB modules:

```bash
docker info   # must connect
```

### Python venv broken / out of date
Recreate the worker venv:

```bash
cd frameflow-ai-worker
rm -rf .venv
python3 -m venv .venv
.venv/bin/pip install -e .
```

### ffmpeg missing
No system FFmpeg is needed — `static-ffmpeg` (a bundled, pinned FFmpeg) is used
by the worker. If the bundled binary is unavailable, reinstall the package
(`pip install -e .`) so `static_ffmpeg` re-downloads its binary.

### "worker not configured" / `WORKER_NOT_CONFIGURED`
The Java batch/slice runner reports this when `frameflow.worker.python` /
`frameflow.worker.cli` are unset. Either set them (Section 1c) or rely on the
default testable path. It is an expected, recoverable message — **not** a product
failure.

---

## 6. Data rebuild / Flyway

Regenerate all runtime media and fixture data from scratch:

```bash
rm -rf data/media                   # app media root (candidate uploads)
rm -rf experiments/fixtures/generated   # synthetic fixtures (reproducible)
# then regenerate fixtures (Section 3) and restart the Java app
```

Flyway migrations are **forward-only** (`V1__baseline.sql` … `V8__review_ranking_selection.sql`,
in `frameflow-app/src/main/resources/db/migration`) and are auto-applied on
startup. Never edit or delete an already-applied migration.
