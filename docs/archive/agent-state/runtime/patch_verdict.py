from pathlib import Path
p = Path('evidence/final/final-verdict.md')
t = p.read_text()
old0 = '- **Web frontend (S7)** is not implemented in this delivery; `frameflow-web` has no'
old1 = '  Next.js source. Recorded as not-applicable / not-run, not as a passing gate.'
newline = '- **Web frontend (S7)** is implemented as a Next.js App Router app under `frameflow-web/`; `npm run build` passes (8 routes type-checked). It is a local desktop-first UI; browser E2E against a live backend is not part of this delivery.'
if old0 in t:
    t = t.replace(old0 + '\n' + old1, newline, 1)
    p.write_text(t)
    print('patched')
else:
    print('not found')
