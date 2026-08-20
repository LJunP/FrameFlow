#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import glob
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

PACK = Path(__file__).resolve().parents[1]
REPO = PACK.parent
MAP_JSON = PACK / 'migration' / 'migration-map.json'


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def run_git(*args: str) -> str:
    proc = subprocess.run(['git', *args], cwd=REPO, text=True, capture_output=True)
    return proc.stdout.strip() if proc.returncode == 0 else ''


def load_map() -> dict:
    if not MAP_JSON.is_file():
        raise RuntimeError(f'missing migration map: {MAP_JSON}')
    return json.loads(MAP_JSON.read_text(encoding='utf-8'))


def safe_remove_tree(path: Path, archive: Path, actions: list[str], dry: bool) -> None:
    if not path.exists():
        return
    try:
        path.relative_to(REPO / 'docs')
    except ValueError as exc:
        raise RuntimeError(f'refuse to remove outside docs: {path}') from exc
    if path == archive or archive in path.parents:
        raise RuntimeError(f'refuse to remove archive: {path}')
    actions.append(f'REMOVE_ACTIVE_LEGACY {path.relative_to(REPO)}')
    if not dry:
        shutil.rmtree(path)


def main() -> int:
    parser = argparse.ArgumentParser(description='Archive legacy FrameFlow docs before installing FrameFlow Select facts.')
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    dry = args.dry_run

    if not (REPO / '.git').exists() or not (REPO / 'pom.xml').exists():
        print(f'NOT_FRAMEFLOW_REPO: {REPO}', file=sys.stderr)
        return 2

    cfg = load_map()
    archive = REPO / cfg['archive_root']
    snapshot_marker = archive / 'LEGACY-SNAPSHOT.json'
    complete_marker = archive / 'MIGRATION-COMPLETE.json'
    actions: list[str] = []

    if complete_marker.exists():
        result = {
            'dryRun': dry,
            'alreadyComplete': True,
            'archive': str(archive.relative_to(REPO)),
            'actions': ['NOOP migration already complete'],
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0

    timestamp = dt.datetime.now(dt.timezone.utc).isoformat()
    baseline = {
        'timestamp': timestamp,
        'head': run_git('rev-parse', 'HEAD'),
        'branch': run_git('branch', '--show-current'),
        'status': run_git('status', '--short', '--branch'),
    }

    # Phase A: immutable legacy snapshot. Never overwrite an existing snapshot.
    snapshot_files: list[dict] = []
    if snapshot_marker.exists():
        actions.append('REUSE_EXISTING_LEGACY_SNAPSHOT')
        try:
            snapshot = json.loads(snapshot_marker.read_text(encoding='utf-8'))
            snapshot_files = snapshot.get('files', [])
        except Exception as exc:
            print(f'INVALID_SNAPSHOT_MARKER: {exc}', file=sys.stderr)
            return 3
    else:
        for rel in cfg['archive_directories']:
            src = REPO / rel
            if not src.exists():
                continue
            dst = archive / Path(rel).name
            actions.append(f'ARCHIVE_COPY {rel} -> {dst.relative_to(REPO)}')
            if not dry:
                if dst.exists():
                    print(f'ARCHIVE_COLLISION: {dst}', file=sys.stderr)
                    return 4
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copytree(src, dst, ignore=shutil.ignore_patterns('core', 'contracts', 'archive'))

        actions.append('WRITE legacy README and snapshot manifest')
        if not dry:
            archive.mkdir(parents=True, exist_ok=True)
            shutil.copy2(PACK / 'migration' / 'templates' / 'LEGACY-README.md', archive / 'README.md')
            for path in sorted(archive.rglob('*')):
                if not path.is_file() or path.name in {'LEGACY-SNAPSHOT.json', 'MIGRATION-COMPLETE.json'}:
                    continue
                snapshot_files.append({
                    'path': str(path.relative_to(archive)),
                    'size': path.stat().st_size,
                    'sha256': sha256(path),
                })
            if not snapshot_files:
                print('EMPTY_LEGACY_SNAPSHOT', file=sys.stderr)
                return 5
            snapshot_marker.write_text(json.dumps({
                'schemaVersion': '1.0.0',
                'baseline': baseline,
                'files': snapshot_files,
            }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    # Dry run ends before any destructive cleanup.
    if dry:
        for rel in cfg['archive_directories']:
            if (REPO / rel).exists():
                actions.append(f'WOULD_REMOVE_ACTIVE_LEGACY {rel}')
        for item in cfg.get('restore_after_archive', []):
            actions.append(f"WOULD_RESTORE {item['source']} -> {item['target']}")
        print(json.dumps({
            'dryRun': True,
            'baseline': baseline,
            'archive': str(archive.relative_to(REPO)),
            'actions': actions,
        }, ensure_ascii=False, indent=2))
        return 0

    # Ensure the on-disk snapshot exists before removing current legacy directories.
    if not snapshot_marker.is_file():
        print('SNAPSHOT_NOT_DURABLE', file=sys.stderr)
        return 6

    # Build cancellation registry from the snapshot, not from active docs.
    cancelled: list[dict] = []
    for pattern in cfg.get('cancel_task_patterns', []):
        for filename in glob.glob(str(REPO / pattern)):
            path = Path(filename)
            try:
                data = json.loads(path.read_text(encoding='utf-8'))
            except Exception:
                continue
            task_id = data.get('taskId') or data.get('id') or path.stem
            cancelled.append({
                'taskId': task_id,
                'source': str(path.relative_to(REPO)),
                'status': 'CANCELLED_BY_FRAMEFLOW_SELECT_PIVOT',
            })

    # Phase B: remove duplicate active legacy facts. The snapshot is already durable.
    for rel in cfg['archive_directories']:
        safe_remove_tree(REPO / rel, archive, actions, dry=False)

    # Restore only explicitly retained identity contracts/data from the archive.
    for item in cfg.get('restore_after_archive', []):
        src = REPO / item['source']
        dst = REPO / item['target']
        if not src.is_file():
            actions.append(f"RESTORE_SKIPPED_MISSING {item['source']}")
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        actions.append(f"RESTORE {item['source']} -> {item['target']}")

    cancelled_target = REPO / 'docs' / '05-engineering' / 'cancelled-tasks.json'
    cancelled_target.parent.mkdir(parents=True, exist_ok=True)
    cancelled_target.write_text(json.dumps({
        'schemaVersion': '1.0.0',
        'reason': 'FRAMEFLOW_SELECT_PIVOT',
        'tasks': sorted(cancelled, key=lambda x: x['taskId']),
    }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    actions.append(f'WRITE cancelled task registry ({len(cancelled)})')

    report = {
        'schemaVersion': '1.0.0',
        'dryRun': False,
        'baseline': baseline,
        'archive': str(archive.relative_to(REPO)),
        'snapshotFileCount': len(snapshot_files),
        'cancelledTasks': cancelled,
        'actions': actions,
        'result': 'LEGACY_DOCS_ARCHIVED_AND_ACTIVE_FACTS_CLEARED',
    }
    complete_marker.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    out = REPO / 'evidence' / 'pivot' / 'legacy-doc-migration.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
