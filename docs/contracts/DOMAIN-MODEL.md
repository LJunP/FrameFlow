# FrameFlow Select 领域模型基线

## Aggregate ownership

```text
Identity: User, Team, TeamMember
Project: Project
Quality: QualityProfile, QualityProfileVersion
Batch: Batch, Candidate, CandidateVersion
Analysis: AnalysisRun, AnalysisStageJob, Finding, EvidenceArtifact, HumanFindingReview
Selection: SimilarityCluster, RankingSnapshot, RankingEntry, SelectionSet, SelectionItem
```

## Main relations

```text
Team 1─* Project
Project 1─* QualityProfile
QualityProfile 1─* QualityProfileVersion
Project 1─* Batch
Batch *─1 QualityProfileVersion
Batch 1─* Candidate
Candidate 1─* CandidateVersion
CandidateVersion 1─* AnalysisRun
AnalysisRun 1─* Finding
Finding 0─* EvidenceArtifact
Finding 0─1 HumanFindingReview per review version
Batch 1─* RankingSnapshot
RankingSnapshot 1─* RankingEntry
Batch 1─* SimilarityCluster
Batch 1─* SelectionSet
SelectionSet 1─* SelectionItem
```

## Invariants

- Team-scoped resources always carry `team_id` and are re-authorized from database facts.
- Published Quality Profile versions are immutable.
- Candidate source files are immutable; replacement creates a new Candidate Version.
- Analysis rerun creates a new Analysis Run.
- Findings remain immutable machine output; human review is a separate fact.
- Ranking Snapshot fixes candidate set, feature versions, weights and algorithm version.
- Locked Selection Set is immutable; edits create a new version.
- Analysis failure is not a quality rejection.
- Provider metadata may be unknown but must never be fabricated.
