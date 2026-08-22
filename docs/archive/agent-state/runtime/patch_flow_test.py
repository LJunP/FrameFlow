from pathlib import Path
p = Path("frameflow-app/src/test/java/com/frameflow/product/ProductFlowIntegrationTest.java")
t = p.read_text()
old = '''        long runId = ((Number) body(start).get("runId")).longValue();

        ResponseEntity<String> ingested = postJson("/api/v1/internal/analysis-runs/" + runId + "/results",
                Map.of("commandId", "cmd_1", "status", "COMPLETED_WITH_FINDINGS",
                        "decision", "{\"value\":\"REVIEW\"}", "qualityVector", "{\"prompt_alignment\":0.8}",
                        "findings", List.of()), h);
        assertThat(ingested.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);'''
new = '''        long runId = ((Number) body(start).get("runId")).longValue();

        ResponseEntity<String> runResp = getJson("/api/v1/analysis-runs/" + runId, tokenHeaders(owner));
        assertThat(runResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String runCommand = String.valueOf(body(runResp).get("commandId"));
        assertThat(runCommand).startsWith("cmd_");

        ResponseEntity<String> ingested = postJson("/api/v1/internal/analysis-runs/" + runId + "/results",
                Map.of("commandId", runCommand, "status", "COMPLETED_WITH_FINDINGS",
                        "decision", "{\"value\":\"REVIEW\"}", "qualityVector", "{\"prompt_alignment\":0.8}",
                        "findings", List.of()), h);
        assertThat(ingested.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);'''
assert old in t, "old not found"
t = t.replace(old, new, 1)
p.write_text(t)
print("patched")
