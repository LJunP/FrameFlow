#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except Exception:
    yaml = None

PACK = Path(__file__).resolve().parents[1]
MANIFEST = PACK / 'manifest.json'
CHECKSUMS = PACK / 'CHECKSUMS.sha256'


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    errors: list[str] = []
    if not MANIFEST.is_file() or not CHECKSUMS.is_file():
        print('PACK_INVALID: manifest or checksums missing', file=sys.stderr)
        return 2

    try:
        manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    except Exception as exc:
        print(f'PACK_INVALID: manifest JSON: {exc}', file=sys.stderr)
        return 2

    if manifest.get('packVersion') != '1.0.0':
        errors.append(f"packVersion:{manifest.get('packVersion')}")
    if manifest.get('product') != 'FrameFlow Select':
        errors.append(f"product:{manifest.get('product')}")

    listed = {item['path']: item for item in manifest.get('files', [])}
    actual = {
        path.relative_to(PACK).as_posix()
        for path in PACK.rglob('*')
        if path.is_file() and path.name not in {'manifest.json', 'CHECKSUMS.sha256'}
    }
    missing = sorted(set(listed) - actual)
    extra = sorted(actual - set(listed))
    errors.extend(f'missing:{item}' for item in missing)
    errors.extend(f'unmanifested:{item}' for item in extra)

    for rel, item in sorted(listed.items()):
        path = PACK / rel
        if not path.is_file():
            continue
        data = path.read_bytes()
        if len(data) != item.get('size'):
            errors.append(f'size:{rel}')
        if hashlib.sha256(data).hexdigest() != item.get('sha256'):
            errors.append(f'digest:{rel}')

    expected_checks = ''.join(f"{listed[rel]['sha256']}  {rel}\n" for rel in sorted(listed))
    if CHECKSUMS.read_text(encoding='utf-8') != expected_checks:
        errors.append('checksums_file_mismatch')

    # Parse all JSON and YAML machine contracts where possible.
    for path in sorted(PACK.rglob('*.json')):
        try:
            json.loads(path.read_text(encoding='utf-8'))
        except Exception as exc:
            errors.append(f'json:{path.relative_to(PACK)}:{exc}')
    for path in sorted(PACK.rglob('*.yaml')):
        text = path.read_text(encoding='utf-8')
        if '\t' in text:
            errors.append(f'yaml_tab:{path.relative_to(PACK)}')
        if yaml is not None:
            try:
                yaml.safe_load(text)
            except Exception as exc:
                errors.append(f'yaml:{path.relative_to(PACK)}:{exc}')

    books = sorted((PACK / 'books').glob('BOOK-*.md'))
    if len(books) != 6:
        errors.append(f'book_count:{len(books)}')
    for rel in [
        'START-HERE.md',
        'DEEPSEEK-HARNESS-MASTER-PROMPT.md',
        'execution/AUTONOMOUS-EXECUTION-CONTRACT.md',
        'execution/MASTER-PLAN.yaml',
        'execution/DECISION-POLICY.yaml',
        'execution/COMPLETION-CRITERIA.yaml',
        'migration/migration-map.json',
        'tools/bootstrap_repository.py',
        'tools/migrate_legacy_docs.py',
        'tools/validate_repository.py',
    ]:
        if not (PACK / rel).is_file():
            errors.append(f'required:{rel}')

    # Lightweight Master Plan structure check even without PyYAML.
    master_text = (PACK / 'execution' / 'MASTER-PLAN.yaml').read_text(encoding='utf-8')
    stage_ids = re.findall(r'^- id: (S\d+)$', master_text, flags=re.MULTILINE)
    if stage_ids != [f'S{i}' for i in range(9)]:
        errors.append(f'stage_ids:{stage_ids}')
    task_ids = re.findall(r'^  - id: (FF-[A-Z]+-\d{3})$', master_text, flags=re.MULTILINE)
    if not task_ids or len(task_ids) != len(set(task_ids)):
        errors.append('task_ids_missing_or_duplicate')

    forbidden_names = [
        path.relative_to(PACK).as_posix()
        for path in PACK.rglob('*')
        if path.name in {'.DS_Store', '__MACOSX'}
    ]
    errors.extend(f'pack_junk:{name}' for name in forbidden_names)

    if errors:
        print('PACK_INVALID')
        for error in errors:
            print(error)
        return 3

    print(json.dumps({
        'status': 'PACK_VALID',
        'version': manifest['packVersion'],
        'files': len(listed),
        'books': len(books),
        'stages': len(stage_ids),
        'tasks': len(task_ids),
    }, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
