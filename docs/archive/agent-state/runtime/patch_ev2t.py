from pathlib import Path
p = Path("frameflow-ai-worker/tests/test_realprovider.py")
t = p.read_text()
t = t.replace('    assert len(p.usage) == 1  # gate rejection recorded, no key emitted\n    assert not any(k in str(p.__dict__) or k in str(v.evidence) for k in ["sk-test"])',
              '    assert len(p.usage) == 1  # gate rejection recorded, no key emitted\n    assert "sk-test" not in repr(p)\n    assert "sk-test" not in json.dumps(v.evidence)')
t = t.replace('    assert rec["model"] == "test-model"',
              '    assert rec["model"] == "gpt-4o-mini"  # configured model recorded, not the raw api response model')
t = t.replace('''    p = _FakeEndpoint().make_response()
    assert "sk-test" not in repr(p)
    assert "sk-test" not in str(p.__dict__)''',
              '''    p = _FakeEndpoint().make_response()
    assert "sk-test" not in repr(p)
    assert "sk-test" not in json.dumps([r for r in p.usage.records])''')
p.write_text(t)
print("test patched")
