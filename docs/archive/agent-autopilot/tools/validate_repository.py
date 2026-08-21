#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def git_lines(*args: str) -> list[str]:
    proc = subprocess.run(['git', *args], cwd=REPO, text=True, capture_output=True)
    return proc.stdout.splitlines() if proc.returncode == 0 else []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', choices=['installed', 'migrated', 'final'], default='installed')
    args = parser.parse_args()
    errors: list[str] = []
    warnings: list[str] = []

    required = [
        'AGENTS.md',
        'docs/core/BOOK-01-项目宪章与权威地图.md',
        'docs/core/BOOK-02-产品与用户体验蓝图.md',
        'docs/core/BOOK-03-AI视频质检标准与评测规范.md',
        'docs/core/BOOK-04-领域模型与系统架构设计.md',
        'docs/core/BOOK-05-开发路线图与阶段门禁.md',
        'docs/core/BOOK-06-研发运行与防偏航手册.md',
        'docs/contracts/quality-profile.schema.json',
        'docs/contracts/analysis-result.schema.json',
        'docs/contracts/worker-command.schema.json',
        'docs/contracts/worker-result.schema.json',
        'docs/contracts/ranking-snapshot.schema.json',
        '.frameflow/AUTONOMOUS-EXECUTION-CONTRACT.md',
        '.frameflow/master-plan.yaml',
        '.frameflow/decision-policy.yaml',
        '.frameflow/completion-criteria.yaml',
        '.frameflow/state.json',
        '.frameflow/schemas/task.schema.json',
        '.frameflow/schemas/state.schema.json',
    ]
    if args.phase in {'migrated', 'final'}:
        required.extend([
            'docs/archive/frameflow-collaboration/README.md',
            'docs/archive/frameflow-collaboration/LEGACY-SNAPSHOT.json',
            'docs/archive/frameflow-collaboration/MIGRATION-COMPLETE.json',
            'docs/05-engineering/cancelled-tasks.json',
            'docs/00-governance/project-status.md',
            'docs/01-product/product-overview.md',
        ])
    if args.phase == 'final':
        required.extend([
            'FINAL-HANDOVER.md',
            'RUNBOOK.md',
            'evidence/final/final-verdict.md',
            'evidence/final/test-summary.json',
            'evidence/final/known-limitations.md',
            'dist/frameflow-select-source.zip',
        ])

    for rel in required:
        if not (REPO / rel).exists():
            errors.append(f'missing:{rel}')

    # JSON syntax and required state.
    json_paths: list[Path] = []
    if (REPO / '.frameflow').exists():
        json_paths.extend((REPO / '.frameflow').rglob('*.json'))
    if (REPO / 'docs' / 'contracts').exists():
        json_paths.extend((REPO / 'docs' / 'contracts').glob('*.json'))
    if (REPO / 'docs' / 'archive' / 'frameflow-collaboration').exists():
        json_paths.extend((REPO / 'docs' / 'archive' / 'frameflow-collaboration').glob('*.json'))
    for path in json_paths:
        try:
            json.loads(path.read_text(encoding='utf-8'))
        except Exception as exc:
            errors.append(f'json:{path.relative_to(REPO)}:{exc}')

    books = list((REPO / 'docs' / 'core').glob('BOOK-*.md')) if (REPO / 'docs' / 'core').exists() else []
    if len(books) != 6:
        errors.append(f'book_count:{len(books)}')

    # Active legacy facts must no longer compete with the new product.
    if args.phase in {'migrated', 'final'}:
        active_legacy = [
            'docs/01-product/FrameFlow-PRD.md',
            'docs/02-architecture/FrameFlow-概要设计.md',
            'docs/03-data/FrameFlow-详细设计.md',
            'docs/05-engineering/development-plan-p0-m17.md',
            'docs/08-learning/48周学习与开发路线图.md',
        ]
        for rel in active_legacy:
            if (REPO / rel).exists():
                errors.append(f'active_legacy_fact:{rel}')

    # Current docs may mention prohibited directions only as explicit non-goals.
    forbidden = ['必须拆分微服务', '必须引入 Kafka', '必须引入 Istio']
    for path in books:
        text = path.read_text(encoding='utf-8', errors='replace')
        for term in forbidden:
            if term in text:
                warnings.append(f'authoritative_term_review:{path.name}:{term}')

    tracked = git_lines('ls-files')
    unsafe_patterns = [
        re.compile(r'(^|/)target/'),
        re.compile(r'\.DS_Store$'),
        re.compile(r'(^|/)__MACOSX/'),
        re.compile(r'(^|/)\.zcode/'),
        re.compile(r'(^|/)data/jwt/.*\.pem$'),
        re.compile(r'(^|/)\.env$'),
    ]
    for rel in tracked:
        if any(pattern.search(rel) for pattern in unsafe_patterns):
            message = f'tracked_unsafe:{rel}'
            if args.phase == 'final':
                errors.append(message)
            else:
                warnings.append(message)

    # Lightweight secret scan over tracked text files; do not print secret values.
    secret_patterns = [
        re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{100,}-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
        re.compile(r'(?i)(?:api[_-]?key|secret|token)\s*[=:]\s*["\']?[A-Za-z0-9_\-]{24,}'),
    ]
    for rel in tracked:
        path = REPO / rel
        if not path.is_file() or path.stat().st_size > 2_000_000:
            continue
        try:
            text = path.read_text(encoding='utf-8')
        except Exception:
            continue
        if any(pattern.search(text) for pattern in secret_patterns):
            message = f'possible_secret:{rel}'
            if args.phase == 'final':
                errors.append(message)
            else:
                warnings.append(message)

    if args.phase == 'final' and (REPO / '.frameflow' / 'state.json').exists():
        state = json.loads((REPO / '.frameflow' / 'state.json').read_text(encoding='utf-8'))
        if state.get('status') != 'LOCAL_MVP_COMPLETE':
            errors.append(f"state_status:{state.get('status')}")
        if state.get('externalValidation') not in {'EXTERNAL_VALIDATION_PENDING', 'UNVERIFIED'}:
            warnings.append(f"external_validation_status:{state.get('externalValidation')}")

    result = {
        'phase': args.phase,
        'errors': sorted(set(errors)),
        'warnings': sorted(set(warnings)),
        'ok': not errors,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not errors else 4


if __name__ == '__main__':
    raise SystemExit(main())
