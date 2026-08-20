---
name: frameflow-select-autopilot
description: Migrate the legacy FrameFlow repository to FrameFlow Select and autonomously build the local portfolio MVP without product drift.
---

# FrameFlow Select Autopilot

Use this skill for every task in this repository.

1. Read root `AGENTS.md`.
2. Read `FRAMEFLOW_SELECT_AUTOPILOT/START-HERE.md`.
3. Verify the pack with `python3 FRAMEFLOW_SELECT_AUTOPILOT/tools/verify_pack.py`.
4. If `.frameflow/state.json` is absent, bootstrap and migrate the repository.
5. Select only the next ready task from `.frameflow/master-plan.yaml`.
6. Create a task capsule, implement, test, verify in a clean worktree, accept or reject, commit, and update state.
7. Continue until `LOCAL_MVP_COMPLETE` or a non-replaceable external blocker is documented.
8. Never change the product into video generation, generic moderation, automatic publishing, or Agent Infra.
9. Never push, deploy, expose secrets, rewrite Git history, or weaken tests and evaluation thresholds.
