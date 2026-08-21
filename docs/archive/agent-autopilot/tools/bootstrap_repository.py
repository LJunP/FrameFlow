#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import glob
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

PACK = Path(__file__).resolve().parents[1]
REPO = PACK.parent


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=REPO, text=True, capture_output=True, check=check)


def copy_file(src: Path, dst: Path, dry: bool, actions: list[str]) -> None:
    actions.append(f"COPY {src.relative_to(REPO)} -> {dst.relative_to(REPO)}")
    if dry:
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)


def write_text(dst: Path, content: str, dry: bool, actions: list[str]) -> None:
    actions.append(f"WRITE {dst.relative_to(REPO)}")
    if dry:
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(content, encoding='utf-8')


def append_unique(dst: Path, block: str, dry: bool, actions: list[str]) -> None:
    current = dst.read_text(encoding='utf-8') if dst.exists() else ''
    if block.strip() in current:
        return
    actions.append(f"APPEND {dst.relative_to(REPO)}")
    if not dry:
        dst.write_text(current.rstrip() + '\n\n' + block.strip() + '\n', encoding='utf-8')


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--dry-run', action='store_true')
    group.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    dry = args.dry_run

    if not (REPO / '.git').exists() or not (REPO / 'pom.xml').exists():
        print(f'NOT_FRAMEFLOW_REPO: {REPO}', file=sys.stderr)
        return 2

    verify = subprocess.run([sys.executable, str(PACK / 'tools' / 'verify_pack.py')], cwd=REPO, text=True)
    if verify.returncode != 0:
        return verify.returncode

    actions: list[str] = []
    timestamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    evidence = REPO / 'evidence' / 'pivot' / 'bootstrap'
    if not dry:
        evidence.mkdir(parents=True, exist_ok=True)

    # Snapshot Git state without changing it.
    git_data = {}
    for name, cmd in {
        'root': ['git', 'rev-parse', '--show-toplevel'],
        'head': ['git', 'rev-parse', 'HEAD'],
        'branch': ['git', 'branch', '--show-current'],
        'status': ['git', 'status', '--short', '--branch'],
        'log': ['git', 'log', '--oneline', '--decorate', '-15'],
        'diff_name_status': ['git', 'diff', '--name-status'],
    }.items():
        proc = run(*cmd, check=False)
        git_data[name] = {'exitCode': proc.returncode, 'stdout': proc.stdout, 'stderr': proc.stderr}
    if not dry:
        (evidence / f'git-baseline-{timestamp}.json').write_text(json.dumps(git_data, ensure_ascii=False, indent=2), encoding='utf-8')
        diff = subprocess.run(['git', 'diff', '--binary'], cwd=REPO, capture_output=True)
        (evidence / f'working-tree-{timestamp}.patch').write_bytes(diff.stdout)

    # Install six books.
    docs_core = REPO / 'docs' / 'core'
    for src in sorted((PACK / 'books').glob('BOOK-*.md')):
        copy_file(src, docs_core / src.name, dry, actions)

    # Install contracts.
    for src in sorted((PACK / 'contracts').iterdir()):
        if src.is_file():
            copy_file(src, REPO / 'docs' / 'contracts' / src.name, dry, actions)

    # Install execution files.
    frameflow = REPO / '.frameflow'
    copy_file(PACK / 'execution' / 'AUTONOMOUS-EXECUTION-CONTRACT.md', frameflow / 'AUTONOMOUS-EXECUTION-CONTRACT.md', dry, actions)
    copy_file(PACK / 'START-HERE.md', frameflow / 'START-HERE.md', dry, actions)
    for name in ['MASTER-PLAN.yaml', 'DECISION-POLICY.yaml', 'COMPLETION-CRITERIA.yaml']:
        copy_file(PACK / 'execution' / name, frameflow / name.lower(), dry, actions)
    for src in sorted((PACK / 'execution' / 'schemas').glob('*')):
        copy_file(src, frameflow / 'schemas' / src.name, dry, actions)
    for src in sorted((PACK / 'execution' / 'templates').glob('*')):
        copy_file(src, frameflow / 'templates' / src.name, dry, actions)
    if not (frameflow / 'state.json').exists():
        copy_file(PACK / 'execution' / 'STATE.initial.json', frameflow / 'state.json', dry, actions)
    actions.append('MKDIR .frameflow/tasks')
    if not dry:
        (frameflow / 'tasks').mkdir(parents=True, exist_ok=True)

    # Install current docs entry files and root templates.
    template = PACK / 'migration' / 'templates'
    copy_file(template / 'ROOT-README.md', REPO / 'README.md', dry, actions)
    copy_file(template / 'CONTRIBUTING.md', REPO / 'CONTRIBUTING.md', dry, actions)
    for rel in [
        'documentation-policy.md', 'project-status.md', 'glossary.md', 'packaging-policy.md'
    ]:
        copy_file(template / rel, REPO / 'docs' / '00-governance' / rel, dry, actions)
    for rel in [
        '00-governance/README.md', '01-product/product-overview.md', '02-architecture/README.md',
        '03-data/README.md', '04-api/README.md', '05-engineering/README.md',
        '06-testing/README.md', '07-operations/README.md', '08-learning/README.md',
        '09-delivery/README.md'
    ]:
        copy_file(template / rel, REPO / 'docs' / rel, dry, actions)

    gitignore_block = """
# FrameFlow Select generated/local artifacts
.frameflow/runtime/
.frameflow/worktrees/
**/target/
frameflow-web/node_modules/
frameflow-web/.next/
frameflow-ai-worker/.venv/
frameflow-ai-worker/**/__pycache__/
experiments/fixtures/generated/
media/
derived-media/
model-cache/
*.pem
.env
.DS_Store
__MACOSX/
.zcode/
"""
    append_unique(REPO / '.gitignore', gitignore_block, dry, actions)

    # Copy migration instructions and pack source manifest into .frameflow.
    copy_file(PACK / 'migration' / 'MIGRATION-RUNBOOK.md', frameflow / 'MIGRATION-RUNBOOK.md', dry, actions)
    copy_file(PACK / 'migration' / 'migration-map.yaml', frameflow / 'migration-map.yaml', dry, actions)
    copy_file(PACK / 'migration' / 'migration-map.json', frameflow / 'migration-map.json', dry, actions)
    copy_file(PACK / 'migration' / 'source-baseline.json', frameflow / 'source-baseline.json', dry, actions)
    copy_file(PACK / 'manifest.json', frameflow / 'bootstrap-pack-manifest.json', dry, actions)

    report = {'timestamp': timestamp, 'dryRun': dry, 'repo': str(REPO), 'actions': actions}
    if not dry:
        (evidence / 'install-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
