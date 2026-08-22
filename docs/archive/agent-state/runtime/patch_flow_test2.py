from pathlib import Path
p = Path("frameflow-app/src/test/java/com/frameflow/product/ProductFlowIntegrationTest.java")
t = p.read_text()
old = '        long runId = ((Number) body(start).get("runId")).longValue();'
if old not in t:
    print("anchor missing")
else:
    # Replace the ingest block: find the upload-session->complete->start->ingest sequence and inject run fetch
    start_marker = '        long runId = ((Number) body(start).get("runId")).longValue();'
    ingest_marker_old = '        ResponseEntity<String> ingested = postJson("/api/v1/internal/analysis-runs/" + runId + "/results",'
    idx = t.find(start_marker)
    assert idx != -1
    # find the ingest declaration line and insert run fetch before it
    inj = t.find(ingest_marker_old)
    assert inj != -1
    inject = ('        ResponseEntity<String> runResp = getJson("/api/v1/analysis-runs/" + runId, tokenHeaders(owner));\n'
              '        assertThat(runResp.getStatusCode()).isEqualTo(HttpStatus.OK);\n'
              '        String runCommand = String.valueOf(body(runResp).get("commandId"));\n'
              '        assertThat(runCommand).startsWith("cmd_");\n\n')
    t = t[:inj] + inject + t[inj:]
    # replace the commandId literal in the ingest body
    t = t.replace('Map.of("commandId", "cmd_1", "status", "COMPLETED_WITH_FINDINGS",',
                  'Map.of("commandId", runCommand, "status", "COMPLETED_WITH_FINDINGS",')
    p.write_text(t)
    print("patched")
