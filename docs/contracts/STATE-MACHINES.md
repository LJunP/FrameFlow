# FrameFlow Select 状态机

## Quality Profile Version

```text
DRAFT → PUBLISHED → RETIRED
```

Published cannot return to Draft.

## Batch

```text
DRAFT → UPLOADING → READY → QUEUED → PROCESSING
PROCESSING → REVIEW_READY | PARTIAL_FAILURE | FAILED | CANCELLED
REVIEW_READY / PARTIAL_FAILURE → SELECTING → COMPLETED
```

## Candidate Version

```text
UPLOADING → STORED → READY_FOR_ANALYSIS
UPLOADING → UPLOAD_FAILED
READY_FOR_ANALYSIS → ANALYZING → ANALYZED | ANALYSIS_ERROR
ANALYSIS_ERROR → ANALYZING only through a new Analysis Run
```

## Analysis Run

```text
CREATED → QUEUED → RUNNING
RUNNING → COMPLETED | COMPLETED_WITH_FINDINGS | FAILED | CANCELLED | TIMED_OUT
FAILED / TIMED_OUT → new run, not in-place reset
```

## Finding review

```text
UNREVIEWED → CONFIRMED | REJECTED | MODIFIED
```

Machine result remains unchanged.

## Selection Set

```text
DRAFT → LOCKED → SUPERSEDED
```
