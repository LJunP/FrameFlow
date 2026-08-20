# FrameFlow Select API Surface

Identity endpoints remain under `/auth` and `/teams` until a versioned migration is approved.

New product endpoints use `/api/v1`:

```text
/projects
/projects/{projectId}
/projects/{projectId}/quality-profiles
/quality-profiles/{profileId}/versions
/quality-profile-versions/{versionId}/publish
/projects/{projectId}/batches
/batches/{batchId}
/batches/{batchId}/candidates
/candidates/{candidateId}/upload-session
/candidates/{candidateId}/upload-complete
/batches/{batchId}/analysis-runs
/analysis-runs/{runId}
/analysis-runs/{runId}/cancel
/candidates/{candidateId}/findings
/findings/{findingId}/review
/batches/{batchId}/similarity-clusters
/batches/{batchId}/ranking-snapshots
/batches/{batchId}/selection-sets
/selection-sets/{selectionSetId}/lock
/selection-sets/{selectionSetId}/export
```

Contract rules:

- Handwritten OpenAPI is authoritative.
- Every mutating create/start/complete endpoint defines idempotency semantics.
- Every resource has team authorization and 404 anti-enumeration behavior.
- DTOs never expose persistence entities.
- Async start returns 202 and a status resource.
- Error body uses one common schema and stable error code registry.
