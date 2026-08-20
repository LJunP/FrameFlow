#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, os, shutil, zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DIST = REPO / 'dist'
OUT = DIST / 'frameflow-select-source.zip'
EXCLUDE_PARTS = {'.git','target','node_modules','.next','__pycache__','.venv','.zcode','__MACOSX','model-cache','generated'}
EXCLUDE_NAMES = {'.DS_Store','.env'}

def allowed(path: Path) -> bool:
    rel = path.relative_to(REPO)
    if any(part in EXCLUDE_PARTS for part in rel.parts): return False
    if path.name in EXCLUDE_NAMES: return False
    if path.suffix == '.pem': return False
    if rel.parts and rel.parts[0] in {'media','derived-media'}: return False
    if rel.parts and rel.parts[0] == 'dist': return False
    return True

DIST.mkdir(parents=True, exist_ok=True)
manifest = []
with zipfile.ZipFile(OUT, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path in sorted(REPO.rglob('*')):
        if not path.is_file() or not allowed(path): continue
        rel = path.relative_to(REPO)
        data = path.read_bytes()
        info = zipfile.ZipInfo(str(Path('FrameFlow-Select') / rel))
        info.flag_bits |= 0x800
        mode = path.stat().st_mode & 0o777
        if mode == 0:
            mode = 0o644
        info.external_attr = (mode & 0xFFFF) << 16
        zf.writestr(info, data)
        manifest.append({'path':str(rel),'size':len(data),'sha256':hashlib.sha256(data).hexdigest()})
(DIST / 'source-manifest.json').write_text(json.dumps({'files':manifest}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{OUT} {hashlib.sha256(OUT.read_bytes()).hexdigest()} files={len(manifest)}')
