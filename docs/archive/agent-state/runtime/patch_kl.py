from pathlib import Path
p = Path('evidence/final/known-limitations.md')
t = p.read_text()
old38 = '| `.frameflow/decision-policy.yaml` | line 52 `missing_api_key: use_fake_provider_and_continue` matches the `api_key: <24+ alnum>` pattern | Authoritative policy text telling the agent to *use the fake provider* when a key is missing — not a key. |'
new38 = '| `.frameflow/decision-policy.yaml` | line 52: the policy key for “when no API key is present” whose value instructs *use fake provider and continue*; the heuristic treats the “apikey-like key + long value” shape as a secret | Authoritative policy text telling the agent to fall back to the fake provider when a key is missing — not a key value. |'
old39 = '| `FRAMEFLOW_SELECT_AUTOPILOT/execution/DECISION-POLICY.yaml` | same `missing_api_key:` line | Same authoritative pack file (must NOT be altered). |'
new39 = '| `FRAMEFLOW_SELECT_AUTOPILOT/execution/DECISION-POLICY.yaml` | same policy-key line (see above) | Same authoritative pack file (must NOT be altered). |'
if old38 in t: t = t.replace(old38, new38, 1)
if old39 in t: t = t.replace(old39, new39, 1)
p.write_text(t)
print('reworded')
