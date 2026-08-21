#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
STATE = REPO / '.frameflow' / 'state.json'


def atomic_write(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
            f.flush()
            os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def main() -> int:
    parser = argparse.ArgumentParser(description='Atomically update FrameFlow Select autonomous execution state.')
    parser.add_argument('--status')
    parser.add_argument('--stage')
    parser.add_argument('--task')
    parser.add_argument('--accept-task')
    parser.add_argument('--gate')
    parser.add_argument('--gate-verdict', choices=['PASS', 'FAIL', 'INCONCLUSIVE', 'OWNER_PROXY_ACCEPTED_LOCAL'])
    parser.add_argument('--blocker')
    parser.add_argument('--decision')
    parser.add_argument('--commit')
    parser.add_argument('--increment-attempt')
    args = parser.parse_args()

    if not STATE.is_file():
        raise SystemExit(f'missing state file: {STATE}')
    state = json.loads(STATE.read_text(encoding='utf-8'))
    now = dt.datetime.now(dt.timezone.utc).isoformat()

    if args.status:
        state['status'] = args.status
    if args.stage:
        state['activeStage'] = None if args.stage == 'NONE' else args.stage
    if args.task:
        state['activeTask'] = None if args.task == 'NONE' else args.task
    if args.accept_task:
        accepted = state.setdefault('acceptedTasks', [])
        if args.accept_task not in accepted:
            accepted.append(args.accept_task)
        state['lastAcceptedTask'] = args.accept_task
        state.setdefault('history', []).append({'at': now, 'event': 'TASK_ACCEPTED', 'taskId': args.accept_task, 'commit': args.commit})
    if args.gate:
        verdict = args.gate_verdict or 'OWNER_PROXY_ACCEPTED_LOCAL'
        state.setdefault('stageGates', {})[args.gate] = {'verdict': verdict, 'at': now, 'commit': args.commit}
        state['lastGate'] = args.gate
        state.setdefault('history', []).append({'at': now, 'event': 'STAGE_GATE', 'gate': args.gate, 'verdict': verdict, 'commit': args.commit})
    if args.blocker:
        state.setdefault('blockers', []).append({'at': now, 'message': args.blocker})
    if args.decision:
        state.setdefault('decisions', []).append({'at': now, 'decision': args.decision})
    if args.commit:
        state['currentCommit'] = args.commit
    if args.increment_attempt:
        attempts = state.setdefault('attempts', {})
        attempts[args.increment_attempt] = int(attempts.get(args.increment_attempt, 0)) + 1
    state['updatedAt'] = now
    atomic_write(STATE, state)
    print(json.dumps(state, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
