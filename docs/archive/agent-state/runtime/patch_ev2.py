from pathlib import Path
p = Path("frameflow-ai-worker/src/frameflow_ai/realprovider.py")
t = p.read_text()

# 1) private api key + repr hiding + disabled ALSO records a gate-rejection usage row
t = t.replace("        self.api_key = os.environ.get(\"FRAMEFLOW_REAL_PROVIDER_API_KEY\", \"\")",
              "        self._api_key = os.environ.get(\"FRAMEFLOW_REAL_PROVIDER_API_KEY\", \"\")")
t = t.replace("        self.enabled = enabled_flag and bool(self.api_key) and budget > 0",
              "        self.enabled = enabled_flag and bool(self._api_key) and budget > 0")
t = t.replace("        if not self.enabled:\n            return SemanticVerdict(assertion_id, \"ERROR\", 0.0,\n                                   \"real provider not enabled (missing gate/key/budget)\",\n                                   evidence={\"enabled\": False})",
              "        if not self.enabled:\n            self.usage.record(kind=\"gate_rejected\", assertionId=assertion_id,\n                              provider=self.provider_id, enabled=False)\n            return SemanticVerdict(assertion_id, \"ERROR\", 0.0,\n                                   \"real provider not enabled (missing gate/key/budget)\",\n                                   evidence={\"enabled\": False})")
t = t.replace("                    \"Authorization\": \"Bearer \" + self.api_key,",
              "                    \"Authorization\": \"Bearer \" + self._api_key,")
# 2) ledger model from configured self.model (fixed config), richer usage
t = t.replace("        self.usage.record(kind=\"ok\", assertionId=assertion_id, provider=self.provider_id,\n                          model=self.model, promptVersion=self.prompt_version,",
              "        self.usage.record(kind=\"ok\", assertionId=assertion_id, provider=self.provider_id,\n                          model=self.model, promptVersion=self.prompt_version, endpoint=self.base_url,")
# 3) __repr__ hiding the key
t = t.replace("    def evaluate(self, assertion: dict, context: dict) -> SemanticVerdict:",
              "    def __repr__(self) -> str:\n        return f\"OptionalOpenAiCompatibleProvider(enabled={self.enabled}, model={self.model!r})\n\"\n\n    def evaluate(self, assertion: dict, context: dict) -> SemanticVerdict:")
p.write_text(t)
print("patched")
