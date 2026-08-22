from pathlib import Path
p = Path("frameflow-app/src/test/java/com/frameflow/product/SingleCandidateSliceE2ETest.java")
t = p.read_text()
# compute repo root from test CWD (module dir) -> parent
t = t.replace('    @DynamicPropertySource
    static void workerProperties(DynamicPropertyRegistry registry) {',
'''    private static final Path REPO_ROOT =
            java.nio.file.Paths.get("..").toAbsolutePath().normalize();

    @DynamicPropertySource
    static void workerProperties(DynamicPropertyRegistry registry) {''')
t = t.replace('registry.add("frameflow.storage.root", () -> "data/media-test");',
              'registry.add("frameflow.storage.root", () -> REPO_ROOT.resolve("data/media-test").toString());')
t = t.replace('Path fixture = Path.of("experiments/fixtures/generated/normal_vertical.mp4");',
              'Path fixture = REPO_ROOT.resolve("experiments/fixtures/generated/normal_vertical.mp4");')
t = t.replace('Path stored = Path.of("data/media-test/teams/" + teamId + "/batches/" + batchId + "/candidates/"
                + candidateId + "/v1/source.mp4");',
              'Path stored = REPO_ROOT.resolve("data/media-test/teams/" + teamId + "/batches/" + batchId + "/candidates/"
                + candidateId + "/v1/source.mp4");')
p.write_text(t)
print("patched paths")
