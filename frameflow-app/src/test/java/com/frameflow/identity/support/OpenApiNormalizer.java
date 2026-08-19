package com.frameflow.identity.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reduces a raw OpenAPI document (runtime springdoc JSON or authoritative YAML)
 * to a canonical comparable form: paths->operations->{operationId, security,
 * parameters, requestBody, responses} plus component schemas.
 *
 * <p>Representation differences that do not change the contract are normalized away:
 * <ul>
 *   <li>the runtime mounts the API under {@code /api/v1} (controllers use it as the
 *       base path) while the authoritative YAML declares it via {@code servers.url} and
 *       keeps path keys relative to it — the runtime prefix is stripped;</li>
 *   <li>infra paths outside the M01 API contract ({@code /health}, {@code /readiness})
 *       are excluded;</li>
 *   <li>operations without explicit security inherit the document-level
 *       {@code security} (the contract relies on the global bearerAuth requirement);</li>
 *   <li>{@code $ref} parameters/responses/schemas are resolved against the same
 *       document's components so inline vs referenced declarations are equal;</li>
 *   <li>{@code format} on {@code integer} schemas is ignored (JSON numbers are uniform;
 *       springdoc renders int32/int64 widths that the handwritten contract often leaves
 *       implicit).</li>
 * </ul>
 * Descriptions, examples, servers, info, tags, headers and global security entries are
 * intentionally not compared (X-Request-Id is applied by a filter in every response
 * regardless).
 */
public final class OpenApiNormalizer {

    /** Infra endpoints owned by the P0 foundation, outside the M01 API contract. */
    private static final List<String> INFRA_PATHS = List.of("/health", "/readiness");

    private OpenApiNormalizer() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(Object raw) {
        Map<String, Object> doc = (Map<String, Object>) raw;
        Map<String, Object> components = (Map<String, Object>) doc.get("components");
        Map<String, Object> parameterDefs = components == null ? Map.of() : asMap(components.get("parameters"));
        Map<String, Object> responseDefs = components == null ? Map.of() : asMap(components.get("responses"));
        Map<String, Object> schemaDefs = components == null ? Map.of() : asMap(components.get("schemas"));
        List<Object> globalSecurity = (List<Object>) doc.get("security");

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> normalizedPaths = normalizePaths((Map<String, Object>) doc.get("paths"),
                parameterDefs, responseDefs, globalSecurity);
        out.put("paths", normalizedPaths);
        out.put("schemas", normalizeSchemas(schemaDefs, schemaDefs,
                referencedSchemas(doc, normalizedPaths, schemaDefs, responseDefs, parameterDefs)));
        return out;
    }

    /**
     * Names of schemas transitively reachable from the surviving operations (request
     * bodies and responses). Infra endpoints are filtered out first, so runtime-only
     * schemas such as ProbeResponse never leak into the comparison.
     */
    private static java.util.Set<String> referencedSchemas(Map<String, Object> rawDoc,
            Map<String, Object> normalizedPaths, Map<String, Object> schemaDefs,
            Map<String, Object> responseDefs, Map<String, Object> parameterDefs) {
        java.util.Set<String> referenced = new java.util.LinkedHashSet<>();
        Map<String, Object> rawPaths = (Map<String, Object>) rawDoc.get("paths");
        if (rawPaths != null) {
            for (Map.Entry<String, Object> entry : rawPaths.entrySet()) {
                if (INFRA_PATHS.contains(entry.getKey())) {
                    continue;
                }
                Map<String, Object> ops = (Map<String, Object>) entry.getValue();
                for (Object methodOp : ops.values()) {
                    Map<String, Object> op = (Map<String, Object>) methodOp;
                    Object body = op.get("requestBody");
                    collectSchemaRefs(body, referenced);
                    Object responses = op.get("responses");
                    collectSchemaRefs(responses, referenced);
                }
            }
        }
        // component response/parameter definitions may themselves reference schemas
        // (e.g. components.responses.Unauthorized -> components.schemas.Error)
        for (Object def : responseDefs.values()) {
            collectSchemaRefs(def, referenced);
        }
        for (Object def : parameterDefs.values()) {
            collectSchemaRefs(def, referenced);
        }
        // transitively expand component refs (e.g. MemberList.items -> TeamMember)
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(referenced);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            Object def = schemaDefs.get(name);
            java.util.Set<String> nested = new java.util.LinkedHashSet<>();
            collectSchemaRefs(def, nested);
            for (String next : nested) {
                if (referenced.add(next)) {
                    queue.add(next);
                }
            }
        }
        return referenced;
    }

    @SuppressWarnings("unchecked")
    private static void collectSchemaRefs(Object node, java.util.Set<String> out) {
        if (node == null) {
            return;
        }
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refStr && refStr.startsWith("#/components/schemas/")) {
                out.add(refStr.substring(refStr.lastIndexOf('/') + 1));
                return;
            }
            for (Object value : map.values()) {
                collectSchemaRefs(value, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectSchemaRefs(item, out);
            }
        }
    }

    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Object resolveRef(Object token, Map<String, Object> defs, Map<String, Object> visited) {
        if (!(token instanceof Map<?, ?> map) || !(map.get("$ref") instanceof String ref)) {
            return token;
        }
        String name = ref.substring(ref.lastIndexOf('/') + 1);
        if (visited.containsKey(name)) {
            return visited.get(name);
        }
        Object resolved = defs.get(name);
        if (resolved == null) {
            return token;
        }
        visited.put(name, resolved);
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizePaths(Map<String, Object> paths,
            Map<String, Object> parameterDefs, Map<String, Object> responseDefs, List<Object> globalSecurity) {
        Map<String, Object> result = new TreeMap<>();
        if (paths == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            String path = entry.getKey();
            if (INFRA_PATHS.contains(path)) {
                continue;
            }
            String normalizedPath = path.startsWith("/api/v1") ? path.substring("/api/v1".length()) : path;
            Map<String, Object> ops = (Map<String, Object>) entry.getValue();
            Map<String, Object> normalizedOps = new TreeMap<>();
            for (String method : new String[]{"get", "post", "patch", "put", "delete", "head", "options"}) {
                if (!ops.containsKey(method)) {
                    continue;
                }
                normalizedOps.put(method, normalizeOperation((Map<String, Object>) ops.get(method),
                        parameterDefs, responseDefs, globalSecurity));
            }
            result.put(normalizedPath, normalizedOps);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeOperation(Map<String, Object> op,
            Map<String, Object> parameterDefs, Map<String, Object> responseDefs, List<Object> globalSecurity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operationId", op.get("operationId"));
        Object security = op.get("security");
        List<Object> effectiveSecurity = security == null
                ? (globalSecurity == null ? List.of() : globalSecurity)
                : (List<Object>) security;
        out.put("security", normalizeSecurity(effectiveSecurity));
        out.put("parameters", normalizeParameters((List<Object>) op.get("parameters"), parameterDefs));
        Object body = op.get("requestBody");
        out.put("requestBody", body == null ? null : schemaRefOf(contentOf((Map<String, Object>) body)));
        out.put("responses", normalizeResponses((Map<String, Object>) op.get("responses"), responseDefs));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeSecurity(List<Object> security) {
        List<String> result = new ArrayList<>();
        if (security == null || security.isEmpty()) {
            return List.of("none");
        }
        for (Object requirement : security) {
            Map<String, Object> req = (Map<String, Object>) requirement;
            List<String> schemes = new ArrayList<>(new TreeMap<>(req).keySet());
            result.add(String.join(",", schemes));
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeParameters(List<Object> parameters, Map<String, Object> parameterDefs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (parameters == null) {
            return result;
        }
        for (Object parameter : parameters) {
            Map<String, Object> visited = new LinkedHashMap<>();
            Map<String, Object> p = (Map<String, Object>) resolveRef(parameter, parameterDefs, visited);
            Map<String, Object> schema = (Map<String, Object>) p.get("schema");
            Map<String, Object> norm = new LinkedHashMap<>();
            norm.put("name", p.get("name"));
            norm.put("in", p.get("in"));
            norm.put("required", p.get("required"));
            norm.put("type", schema == null ? null : schema.get("type"));
            norm.put("format", schema == null ? null : schema.get("format"));
            norm.put("enum", schema == null ? null : schema.get("enum"));
            result.add(norm);
        }
        result.sort(Comparator
                .comparing((Map<String, Object> m) -> String.valueOf(m.get("name")))
                .thenComparing(m -> String.valueOf(m.get("in"))));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeResponses(Map<String, Object> responses, Map<String, Object> responseDefs) {
        Map<String, Object> result = new TreeMap<>();
        if (responses == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : responses.entrySet()) {
            Map<String, Object> visited = new LinkedHashMap<>();
            Map<String, Object> response = (Map<String, Object>) resolveRef(entry.getValue(), responseDefs, visited);
            result.put(entry.getKey(), schemaRefOf(contentOf(response)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentOf(Map<String, Object> holder) {
        if (holder == null) {
            return null;
        }
        Map<String, Object> content = (Map<String, Object>) holder.get("content");
        if (content == null || content.isEmpty()) {
            return null;
        }
        return (Map<String, Object>) content.values().iterator().next();
    }

    private static String schemaRefOf(Map<String, Object> content) {
        if (content == null) {
            return null;
        }
        Map<String, Object> schema = (Map<String, Object>) content.get("schema");
        if (schema == null) {
            return null;
        }
        String ref = (String) schema.get("$ref");
        if (ref != null) {
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeSchemas(Map<String, Object> schemas, Map<String, Object> schemaDefs,
            java.util.Set<String> referenced) {
        Map<String, Object> result = new TreeMap<>();
        if (schemas == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : schemas.entrySet()) {
            Map<String, Object> rawSchema = (Map<String, Object>) entry.getValue();
            // A bare enum component (no properties/items) is representation: springdoc inlines
            // enums while the handwritten contract declares a component. The enum values are
            // still compared wherever the property references them, so drop the top-level entry.
            boolean leafEnum = rawSchema.get("enum") instanceof List<?>
                    && rawSchema.get("properties") == null && rawSchema.get("items") == null;
            if (!referenced.isEmpty() && !referenced.contains(entry.getKey())) {
                continue;
            }
            if (leafEnum) {
                continue;
            }
            result.put(entry.getKey(), normalizeSchema(rawSchema, schemaDefs));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeSchema(Map<String, Object> schema, Map<String, Object> schemaDefs) {
        Map<String, Object> visited = new LinkedHashMap<>();
        schema = (Map<String, Object>) resolveRef(schema, schemaDefs, visited);
        Map<String, Object> out = new TreeMap<>();
        out.put("type", schema.get("type"));
        String rawFormat = (String) schema.get("format");
        // Integer widths are representation, not contract: JSON numbers carry no width.
        out.put("format", "integer".equals(schema.get("type")) ? null : rawFormat);
        // nullable and 0-length bounds carry no contract meaning in this comparison:
        // springdoc renders @Schema(nullable=true) inconsistently, and minLength=0 is no constraint.
        out.put("minLength", (schema.get("minLength") instanceof Number num && num.intValue() == 0) ? null : schema.get("minLength"));
        out.put("maxLength", schema.get("maxLength"));
        Object enumValue = schema.get("enum");
        if (enumValue instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparing(String::valueOf));
            out.put("enum", sorted);
        }
        Object items = schema.get("items");
        if (items != null) {
            out.put("items", normalizeSchema((Map<String, Object>) resolveRef(items, schemaDefs, visited), schemaDefs));
        }
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> props) {
            Map<String, Object> normProps = new TreeMap<>();
            for (Map.Entry<?, ?> prop : props.entrySet()) {
                Object propSchema = prop.getValue();
                if (propSchema instanceof Map<?, ?> propMap && propMap.containsKey("$ref")) {
                    Map<String, Object> innerVisited = new LinkedHashMap<>();
                    propSchema = resolveRef(propMap, schemaDefs, innerVisited);
                }
                normProps.put(String.valueOf(prop.getKey()),
                        normalizeSchema((Map<String, Object>) propSchema, schemaDefs));
            }
            out.put("properties", normProps);
        }
        Object required = schema.get("required");
        if (required instanceof List<?> reqList) {
            List<Object> sorted = new ArrayList<>(reqList);
            sorted.sort(Comparator.comparing(String::valueOf));
            out.put("required", sorted);
        }
        return out;
    }

    /** First differing path between two normalized documents, or null when equal. */
    @SuppressWarnings("unchecked")
    public static String firstDifference(Object expected, Object actual, String path) {
        if (expected == null || actual == null) {
            if (expected == null && actual == null) {
                return null;
            }
            return path + ": expected=" + expected + " got=" + actual;
        }
        if (expected instanceof Map<?, ?> expMap && actual instanceof Map<?, ?> actMap) {
            java.util.Set<Object> expKeys = new java.util.TreeSet<>(expMap.keySet());
            java.util.Set<Object> actKeys = new java.util.TreeSet<>(actMap.keySet());
            if (!expKeys.equals(actKeys)) {
                java.util.Set<Object> onlyExp = new java.util.TreeSet<>(expKeys);
                onlyExp.removeAll(actKeys);
                java.util.Set<Object> onlyAct = new java.util.TreeSet<>(actKeys);
                onlyAct.removeAll(expKeys);
                return path + ": key set differs (only expected=" + onlyExp + " only actual=" + onlyAct + ")";
            }
            for (Object key : expKeys) {
                String sub = firstDifference(expMap.get(key), actMap.get(key), path + "." + key);
                if (sub != null) {
                    return sub;
                }
            }
            return null;
        }
        if (expected instanceof List<?> expList && actual instanceof List<?> actList) {
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