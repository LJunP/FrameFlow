#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, shutil, subprocess, sys
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def run(cmd: list[str]) -> None:
    proc = subprocess.run(cmd, text=True, capture_output=True)
    if proc.returncode:
        print(proc.stdout)
        print(proc.stderr, file=sys.stderr)
        raise RuntimeError('ffmpeg failed')


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--output', default='experiments/fixtures/generated')
    p.add_argument('--duration', type=int, default=4)
    args = p.parse_args()
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        (out / 'README.md').write_text('FFmpeg is unavailable; fixture generation is pending.\n', encoding='utf-8')
        print(json.dumps({'status':'FFMPEG_UNAVAILABLE','output':str(out)}))
        return 0

    cases = []
    def make(name: str, video_filter: str, size: str='360x640', audio: bool=True, duration: int|None=None):
        dur = duration or args.duration
        path = out / f'{name}.mp4'
        if video_filter == 'testsrc2':
            source = f'testsrc2=size={size}:rate=24'
        elif video_filter.startswith('color='):
            source = f'{video_filter}:size={size}:rate=24'
        elif video_filter == 'smptebars':
            source = f'smptebars=size={size}:rate=24'
        else:
            raise ValueError(f'unsupported synthetic source: {video_filter}')
        cmd = [ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', source]
        if audio:
            cmd += ['-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000']
        cmd += ['-t', str(dur), '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '28', '-pix_fmt', 'yuv420p']
        if audio:
            cmd += ['-c:a', 'aac', '-shortest']
        cmd += ['-movflags', '+faststart', str(path)]
        run(cmd)
        cases.append({'id': name, 'file': path.name, 'sha256': sha256(path)})
        return path

    duration = args.duration
    make('normal_vertical', 'testsrc2')
    make('wrong_aspect', 'testsrc2', size='640x360')
    make('silent_vertical', 'testsrc2', audio=False)
    make('black_video', 'color=c=black')
    make('freeze_video', 'color=c=blue')
    make('too_short', 'testsrc2', duration=1)
    original = make('duplicate_source', 'testsrc2')
    duplicate = out / 'duplicate_copy.mp4'
    shutil.copy2(original, duplicate)
    cases.append({'id':'duplicate_copy','file':duplicate.name,'sha256':sha256(duplicate)})
    make('near_duplicate', 'smptebars')
    make('low_resolution', 'testsrc2', size='180x320')

    labels = {
        'normal_vertical': {'expected': ['DECODE_OK','ASPECT_9_16','HAS_AUDIO']},
        'wrong_aspect': {'expected': ['ASPECT_MISMATCH']},
        'silent_vertical': {'expected': ['AUDIO_MISSING']},
        'black_video': {'expected': ['BLACK_INTERVAL']},
        'freeze_video': {'expected': ['FREEZE_INTERVAL']},
        'too_short': {'expected': ['DURATION_TOO_SHORT']},
        'duplicate_source': {'duplicateGroup':'exact-1'},
        'duplicate_copy': {'duplicateGroup':'exact-1'},
        'near_duplicate': {'expected': ['REVIEW_SIMILARITY']},
        'low_resolution': {'expected': ['RESOLUTION_TOO_LOW']}
    }
    manifest = {'schemaVersion':'1.0.0','source':'SYNTHETIC_FFMPEG','cases':[]}
    for case in cases:
        case.update(labels.get(case['id'], {}))
        manifest['cases'].append(case)
    (out / 'dataset-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status':'OK','output':str(out),'cases':len(cases)}, ensure_ascii=False))
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
