package com.frameflow.identity.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Standalone runtime OpenAPI comparison driver used by
 * scripts/m01/check-openapi-diff.sh (EV-FF-M01-001-04).
 *
 * Usage: OpenApiDiffRunner <runtime-openapi.json> <authoritative.yaml>
 * Applies the exact OpenApiNormalizer used by OpenApiDiffTest to both documents and
 * exits 0 when the normalized contracts are equal, 1 otherwise (printing the diff).
 * Not executed by surefire (name does not match the test patterns).
 */
public final class OpenApiDiffRunner {

    private OpenApiDiffRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: OpenApiDiffRunner <runtime.json> <authoritative.yaml>");
            System.exit(2);
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> runtime =
                mapper.readValue(Files.readString(Path.of(args[0])), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, Object> contract = new Yaml().load(Files.readString(Path.of(args[1])));

        Map<String, Object> runtimeNorm = OpenApiNormalizer.normalize(runtime);
        Map<String, Object> contractNorm = OpenApiNormalizer.normalize(contract);

        System.out.println("runtime contract: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(runtimeNorm));
        System.out.println();
        System.out.println("authoritative contract: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contractNorm));
        System.out.println();

        String firstDiff = firstDifference(contractNorm, runtimeNorm, "$");
        if (firstDiff == null) {
            System.out.println("OPENAPI-DIFF: PASS (normalized runtime OpenAPI equals authoritative contract)");
            System.exit(0);
        }
        System.out.println("OPENAPI-DIFF: FAIL at " + firstDiff);
        System.exit(1);
    }

    @SuppressWarnings("unchecked")
    private static String firstDifference(Object expected, Object actual, String path) {
        if (expected == null || actual == null) {
            if (expected == null && actual == null) {
                return null;
            }
            return path + ": expected=" + expected + " got=" + actual;
        }
        if (expected instanceof Map<?, ?> expMap && actual instanceof Map<?, ?> actMap) {
            if (!new java.util.TreeMap<>(expMap).keySet().equals(new java.util.TreeMap<>(actMap).keySet())) {
                java.util.Set<Object> onlyExp = new java.util.TreeSet<>(expMap.keySet());
                onlyExp.removeAll(actMap.keySet());
                java.util.Set<Object> onlyAct = new java.util.TreeSet<>(actMap.keySet());
                onlyAct.removeAll(expMap.keySet());
                return path + ": key set differs (only expected=" + onlyExp + " only actual=" + onlyAct + ")";
            }
            for (Object key : new java.util.TreeMap<>(expMap).keySet()) {
                String sub = firstDifference(expMap.get(key), actMap.get(key), path + "." + key);
                if (sub != null) {
                    return sub;
                }
            }
            return null;
        }
        if (expected instanceof java.util.List<?> expList && actual instanceof java.util.List<?> actList) {
            if (expList.size() != actList.size()) {
                return path + ": list size differs expected=" + expList.size() + " got=" + actList.size();
            }
            for (int i = 0; i < expList.size(); i++) {
                String sub = firstDifference(expList.get(i), actList.get(i), path + "[" + i + "]");
                if (sub != null) {
                    return sub;
                }
            }
            return null;
        }
        if (!expected.equals(actual)) {
            return path + ": expected=" + expected + " got=" + actual;
        }
        return null;
    }
}
