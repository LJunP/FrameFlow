from pathlib import Path
p = Path("frameflow-ai-worker/src/frameflow_ai/cli.py")
t = p.read_text()
insert_fn = '''

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
anchor = 'def main_probe(argv: list[str]) -> int:'
assert anchor in t
t = t.replace(anchor, insert_fn + "
" + anchor, 1)
# dispatch branch after eval
old_dispatch = '    if cmd == "eval":
        from .eval import main_eval
        return main_eval(rest)'
new_dispatch = old_dispatch + '
    if cmd in {"providers", "provider-info"}:
        return main_provider_info(rest)'
assert old_dispatch in t
t = t.replace(old_dispatch, new_dispatch, 1)
p.write_text(t)
print("cli patched")
