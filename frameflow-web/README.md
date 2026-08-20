# FrameFlow Web - Select product experience (S7, FF-WEB-001/002/003)

Next.js (App Router) + React + TypeScript + Tailwind CSS web application for the
**FrameFlow Select** platform: batch quality review, evidence review and Top-K
selection for AI-generated short-video teams.

The app is a thin, typed client over the FrameFlow Select Java backend
(`frameflow-modules/product`), which exposes the full `/api/v1` HTTP API
(see `docs/04-api/openapi/frameflow-v1.yaml`).

## Stack

- Next.js 15 (App Router) with React 19
- TypeScript (strict)
- Tailwind CSS 3 via PostCSS
- No UI framework beyond Tailwind; native HTML5 `<video>` player for review

## Pages

| Route | Purpose |
|---|---|
| `/login` | Sign in / create account |
| `/dashboard` | Select / create team, list + create projects |
| `/projects/[id]` | Quality profiles (JSON version editor + publish) and batches |
| `/batches/[id]` | Multi-file candidate upload (progress / retry / errors), start analysis, poll progress, candidate matrix with status filters |
| `/candidates/[id]` | Video player with a timecoded Finding timeline that seeks the player |
| `/batches/[id]/selection` | Similarity clusters, ranking breakdown, build + lock + export selection set (Top-K) |

## Setup

```bash
cd frameflow-web
npm install
```

### Environment

Copy `.env.example` to `.env` (or export) and point the app at the backend:

```bash
# Base URL of the FrameFlow Select Java backend (all /api/v1 endpoints)
FRAMEFLOW_API=http://127.0.0.1:8080

# Optional: media base used to build candidate video stream URLs in the reviewer.
# Defaults to FRAMEFLOW_API.
# FRAMEFLOW_MEDIA=https://cdn.example.com/media
```

`FRAMEFLOW_API` defaults to `http://127.0.0.1:8080` when unset, so the app runs
against a locally-started backend without any configuration.

## Run

```bash
cd frameflow-web
npm run dev      # http://localhost:3000
# or
npm run build    # production build (type-checked)
npm run start    # serve the production build
```

### Validation

```bash
cd frameflow-web && npm install && npm run build
```

The build runs full type-checking (strict) and works with the backend **offline** -
only runtime API calls need a live backend. With the backend up, register a user,
create a team, then create a project -> quality profile -> batch -> upload candidates -> run analysis.

## Project layout (self-contained workspace)

```
frameflow-web/
  app/                 # App Router pages (login, dashboard, projects, batches, candidates, selection)
  components/          # AppShell nav, UI primitives, toast, video review player
  lib/
    api.ts             # Typed API client (auth, teams, product; Bearer + X-Team-Id)
    types.ts           # Domain types mirroring the OpenAPI contract
    store.tsx          # Client session store (token + active team)
    utils.ts           # cn, formatting helpers
  package.json
  tsconfig.json
  next.config.mjs
  tailwind.config.ts
  postcss.config.mjs
```

## Security note (token storage)

For developer convenience this MVP web shell persists the **access token in
`localStorage`** (see `lib/store.tsx`). This is acceptable for local development
but **not** for production: tokens should be moved behind a **BFF / HttpOnly
cookie** so the access/refresh token is never readable from JavaScript and the
`X-Team-Id` scoping header is applied server-side. The backend enforces all
authorization regardless of UI state.

## Assumptions

- **Register** (`POST /auth/register`) returns the user object, **not** a token
  pair (per the OpenAPI contract); the UI asks the user to sign in after registering.
  Logging in (`POST /auth/login`) returns the `{accessToken, refreshToken, expiresIn, tokenType}` pair.
- **Quality profile versions** are tracked in local session state after creation
  (the OpenAPI contract has no list-versions GET endpoint; `GET /projects/{id}/quality-profiles`
  returns profiles only). Create + publish a version before creating a batch.
- **Batch "start analysis"** uses `POST /batches/{id}/process` (process all
  candidates) followed by polling `POST /batches/{id}/progress`. Individual
  reruns use `POST /candidates/{id}/rerun`.
- **Upload flow** per the contract: add candidate -> `POST /candidates/{id}/upload-session`
  -> `POST /candidates/{id}/upload-complete` `{sessionId, sizeBytes, contentDigest}`
  (SHA-256 computed in-browser via Web Crypto). This is the structured-handshake
  flow; the raw object-streaming endpoint is out of scope for this shell.
- **Candidate video streaming** uses a best-effort media URL convention
  (`{FRAMEFLOW_MEDIA}/api/v1/media/candidates/{id}/stream`); the reviewer video
  element degrades gracefully when no media endpoint is reachable.

## Related docs

- `docs/04-api/openapi/frameflow-v1.yaml` - authoritative API contract
- `docs/core/BOOK-02-产品与用户体验蓝图.md` - product & UX intent (batch matrix, evidence review, Top-K compare)
