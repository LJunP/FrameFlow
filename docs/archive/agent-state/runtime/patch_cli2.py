import sys
from pathlib import Path
p = Path(sys.argv[1])
t = p.read_text()
anchor_fn = "def main_probe(argv: list[str]) -> int:"
anchor_dispatch = '    if cmd == "eval":\n        from .eval import main_eval\n        return main_eval(rest)'
print("anchor_fn:", anchor_fn in t)
print("anchor_dispatch:", anchor_dispatch in t)
if anchor_fn in t and anchor_dispatch in t:
    fn = '''

def main_provider_info(argv: list[str]) -> int:
    import os
    from .realprovider import OptionalOpenAiCompatibleProvider
    p = OptionalOpenAiCompatibleProvider()
    enabled_flag = os.environ.get("FRAMEFLOW_REAL_PROVIDER_ENABLED", "").strip().lower() == "true"
    has_key = bool(os.environ.get("FRAMEFLOW_REAL_PROVIDER_API_KEY", ""))
    budget_raw = os.environ.get("FRAMEFLOW_EXTERNAL_VALIDATION_BUDGET_CNY", "")
    def budget_ok():
        if not budget_raw:
            return False
        try:
            return float(budget_raw) > 0
        except ValueError:
            return False
    doc = {
        "provider": "openai-compatible",
        "adapter": "OptionalOpenAiCompatibleProvider",
        "version": p.provider_version,
        "enabled": p.enabled,
        "gates": {
            "FRAMEFLOW_REAL_PROVIDER_ENABLED": enabled_flag,
            "apiKeyProvided": has_key,
            "budgetProvided": budget_ok(),
        },
        "model": p.model,
        "note": "API key value is never printed by this command.",
    }
    print(json.dumps(doc, ensure_ascii=False, indent=2))
    return 0


'''
    t = t.replace(anchor_fn, fn + anchor_fn, 1)
    dispatch = '    if cmd in {"providers", "provider-info"}:\n        return main_provider_info(rest)'
    t = t.replace(anchor_dispatch, anchor_dispatch + "\n" + dispatch, 1)
    p.write_text(t)
    print("PATCH OK")
else:
    print("PATCH SKIPPED")
