from pathlib import Path
p = Path("frameflow-app/src/test/java/com/frameflow/product/ProductFlowIntegrationTest.java")
t = p.read_text()
old = '        ResponseEntity<String> lock = postJson("/api/v1/selection-sets/" + setId + "/lock", Map.of(), tokenHeaders(owner));'
new = '        ResponseEntity<String> lock = postJson("/api/v1/selection-sets/" + setId + "/lock", Map.of(), h);'
assert old in t, "lock call missing"
p.write_text(t.replace(old, new, 1))
print("test lock header patched")

p2 = Path("frameflow-modules/product/src/main/java/com/frameflow/product/web/SelectionController.java")
t2 = p2.read_text()
old2 = '''    public ResponseEntity<Map<String, Object>> export(Authentication auth, @PathVariable long selectionSetId) {
        long uid = userId(auth);
        SelectionSet s = service.getSelection(selectionSetId);
        long teamId = batchService.getBatch(service.batchTeamOfSelection(selectionSetId)).getTeamId();
        guard.requireMember(uid, teamId);
        List<Long> ids = service.selectionCandidateIds(0L, selectionSetId);
        return ResponseEntity.ok(Map.of("selectionSetId", selectionSetId, "status", s.getStatus(),
                "candidateIds", ids, "format", "csv-json"));
    }'''
new2 = '''    public ResponseEntity<Map<String, Object>> export(Authentication auth, @PathVariable long selectionSetId,
            @RequestHeader("X-Team-Id") long teamId) {
        long uid = userId(auth);
        SelectionSet s = service.getSelection(teamId, selectionSetId);
        guard.requireMember(uid, teamId);
        List<Long> ids = service.selectionCandidateIds(teamId, selectionSetId);
        return ResponseEntity.ok(Map.of("selectionSetId", selectionSetId, "status", s.getStatus(),
                "candidateIds", ids, "format", "csv-json"));
    }'''
assert old2 in t2, "export pattern missing"
p2.write_text(t2.replace(old2, new2, 1))
print("export endpoint patched")
