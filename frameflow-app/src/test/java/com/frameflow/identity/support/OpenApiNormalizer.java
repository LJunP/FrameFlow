package com.frameflow.identity.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Canonical semantic projection used to compare runtime springdoc output with the
 * handwritten authority. It intentionally retains effective security, security
 * schemes, every request/response media type, response headers, request-body
 * requiredness, and validation constraints that change accepted input.
 */
public final class OpenApiNormalizer {

    private static final List<String> INFRA_PATHS = List.of("/health", "/readiness");
    private static final List<String> HTTP_METHODS =
            List.of("get", "post", "patch", "put", "delete", "head", "options", "trace");

    private OpenApiNormalizer() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(Object raw) {
        Map<String, Object> doc = (Map<String, Object>) raw;
        Map<String, Object> components = asMap(doc.get("components"));
        Map<String, Object> parameterDefs = asMap(components.get("parameters"));
        Map<String, Object> responseDefs = asMap(components.get("responses"));
        Map<String, Object> headerDefs = asMap(components.get("headers"));
        Map<String, Object> schemaDefs = asMap(components.get("schemas"));
        Map<String, Object> securitySchemeDefs = asMap(components.get("securitySchemes"));
        List<Object> globalSecurity = doc.get("security") instanceof List<?> list
                ? (List<Object>) list : List.of();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("securitySchemes", normalizeSecuritySchemes(securitySchemeDefs));
        out.put("paths", normalizePaths(asMap(doc.get("paths")), parameterDefs, responseDefs,
                headerDefs, schemaDefs, globalSecurity));
        out.put("schemas", normalizeSchemas(schemaDefs, schemaDefs,
                referencedSchemas(doc, schemaDefs, responseDefs, parameterDefs)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> referencedSchemas(Map<String, Object> rawDoc,
            Map<String, Object> schemaDefs, Map<String, Object> responseDefs,
            Map<String, Object> parameterDefs) {
        Set<String> referenced = new LinkedHashSet<>();
        Map<String, Object> rawPaths = asMap(rawDoc.get("paths"));
        for (Map.Entry<String, Object> entry : rawPaths.entrySet()) {
            if (INFRA_PATHS.contains(entry.getKey())) {
                continue;
            }
            Map<String, Object> pathItem = asMap(entry.getValue());
            for (String method : HTTP_METHODS) {
                Map<String, Object> operation = asMap(pathItem.get(method));
                if (operation.isEmpty()) {
                    continue;
                }
                collectSchemaRefs(operation.get("parameters"), referenced);
                collectSchemaRefs(operation.get("requestBody"), referenced);
                collectSchemaRefs(operation.get("responses"), referenced);
            }
        }
        responseDefs.values().forEach(def -> collectSchemaRefs(def, referenced));
        parameterDefs.values().forEach(def -> collectSchemaRefs(def, referenced));

        ArrayDeque<String> queue = new ArrayDeque<>(referenced);
        while (!queue.isEmpty()) {
            Object definition = schemaDefs.get(queue.remove());
            Set<String> nested = new LinkedHashSet<>();
            collectSchemaRefs(definition, nested);
            for (String next : nested) {
                if (referenced.add(next)) {
                    queue.add(next);
                }
            }
        }
        return referenced;
    }

    private static void collectSchemaRefs(Object node, Set<String> out) {
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String value && value.startsWith("#/components/schemas/")) {
                out.add(value.substring(value.lastIndexOf('/') + 1));
                return;
            }
            map.values().forEach(value -> collectSchemaRefs(value, out));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> collectSchemaRefs(value, out));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Object resolveRef(Object token, Map<String, Object> definitions) {
        if (!(token instanceof Map<?, ?> map) || !(map.get("$ref") instanceof String ref)) {
            return token;
        }
        return definitions.getOrDefault(ref.substring(ref.lastIndexOf('/') + 1), token);
    }

    private static Map<String, Object> normalizePaths(Map<String, Object> paths,
            Map<String, Object> parameterDefs, Map<String, Object> responseDefs,
            Map<String, Object> headerDefs, Map<String, Object> schemaDefs,
            List<Object> globalSecurity) {
        Map<String, Object> result = new TreeMap<>();
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            if (INFRA_PATHS.contains(entry.getKey())) {
                continue;
            }
            String path = entry.getKey().startsWith("/api/v1")
                    ? entry.getKey().substring("/api/v1".length()) : entry.getKey();
            Map<String, Object> pathItem = asMap(entry.getValue());
            Map<String, Object> operations = new TreeMap<>();
            for (String method : HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    operations.put(method, normalizeOperation(asMap(pathItem.get(method)), parameterDefs,
                            responseDefs, headerDefs, schemaDefs, globalSecurity));
                }
            }
            result.put(path, operations);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeOperation(Map<String, Object> operation,
            Map<String, Object> parameterDefs, Map<String, Object> responseDefs,
            Map<String, Object> headerDefs, Map<String, Object> schemaDefs,
            List<Object> globalSecurity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operationId", operation.get("operationId"));
        Object declaredSecurity = operation.get("security");
        List<Object> effectiveSecurity = declaredSecurity instanceof List<?> list
                ? (List<Object>) list : globalSecurity;
        out.put("security", normalizeSecurity(effectiveSecurity));
        out.put("parameters", normalizeParameters(
                operation.get("parameters") instanceof List<?> list ? (List<Object>) list : List.of(),
                parameterDefs, schemaDefs));
        out.put("requestBody", normalizeRequestBody(asMap(operation.get("requestBody")), schemaDefs));
        out.put("responses", normalizeResponses(asMap(operation.get("responses")), responseDefs,
                headerDefs, schemaDefs));
        return out;
    }

    private static Object normalizeSecurity(List<Object> security) {
        if (security == null || security.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object requirement : security) {
            Map<String, Object> normalized = new TreeMap<>();
            asMap(requirement).forEach((scheme, scopes) -> {
                List<Object> sortedScopes = scopes instanceof List<?> list
                        ? new ArrayList<>(list) : new ArrayList<>();
                sortedScopes.sort(Comparator.comparing(String::valueOf));
                normalized.put(scheme, sortedScopes);
            });
            result.add(normalized);
        }
        result.sort(Comparator.comparing(Object::toString));
        return result;
    }

    private static Map<String, Object> normalizeSecuritySchemes(Map<String, Object> schemes) {
        Map<String, Object> result = new TreeMap<>();
        schemes.forEach((name, rawScheme) -> {
            Map<String, Object> scheme = asMap(rawScheme);
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (String key : List.of("type", "scheme", "bearerFormat", "in", "name", "openIdConnectUrl")) {
                normalized.put(key, scheme.get(key));
            }
            result.put(name, normalized);
        });
        return result;
    }

    private static Object normalizeParameters(List<Object> parameters, Map<String, Object> parameterDefs,
            Map<String, Object> schemaDefs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object parameter : parameters) {
            Map<String, Object> resolved = asMap(resolveRef(parameter, parameterDefs));
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("name", resolved.get("name"));
            normalized.put("in", resolved.get("in"));
            normalized.put("required", Boolean.TRUE.equals(resolved.get("required")));
            normalized.put("schema", normalizeSchemaToken(resolved.get("schema"), schemaDefs));
            result.add(normalized);
        }
        result.sort(Comparator.comparing((Map<String, Object> map) -> String.valueOf(map.get("name")))
                .thenComparing(map -> String.valueOf(map.get("in"))));
        return result;
    }

    private static Object normalizeRequestBody(Map<String, Object> requestBody, Map<String, Object> schemaDefs) {
        if (requestBody.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("required", Boolean.TRUE.equals(requestBody.get("required")));
        normalized.put("content", normalizeContent(asMap(requestBody.get("content")), schemaDefs));
        return normalized;
    }

    private static Object normalizeResponses(Map<String, Object> responses,
            Map<String, Object> responseDefs, Map<String, Object> headerDefs,
            Map<String, Object> schemaDefs) {
        Map<String, Object> result = new TreeMap<>();
        responses.forEach((status, rawResponse) -> {
            Map<String, Object> response = asMap(resolveRef(rawResponse, responseDefs));
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("headers", normalizeHeaders(asMap(response.get("headers")), headerDefs, schemaDefs));
            normalized.put("content", normalizeContent(asMap(response.get("content")), schemaDefs));
            result.put(status, normalized);
        });
        return result;
    }

    private static Object normalizeHeaders(Map<String, Object> headers, Map<String, Object> headerDefs,
            Map<String, Object> schemaDefs) {
        Map<String, Object> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, rawHeader) -> {
            Map<String, Object> header = asMap(resolveRef(rawHeader, headerDefs));
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("required", Boolean.TRUE.equals(header.get("required")));
            normalized.put("schema", normalizeSchemaToken(header.get("schema"), schemaDefs));
            result.put(name, normalized);
        });
        return result;
    }

    private static Object normalizeContent(Map<String, Object> content, Map<String, Object> schemaDefs) {
        Map<String, Object> result = new TreeMap<>();
        content.forEach((mediaType, rawMedia) -> result.put(mediaType,
                normalizeSchemaToken(asMap(rawMedia).get("schema"), schemaDefs)));
        return result;
    }

    private static Object normalizeSchemaToken(Object rawSchema, Map<String, Object> schemaDefs) {
        Map<String, Object> schema = asMap(rawSchema);
        if (schema.isEmpty()) {
            return null;
        }
        Object ref = schema.get("$ref");
        if (ref instanceof String value) {
            return Map.of("$ref", value.substring(value.lastIndexOf('/') + 1));
        }
        return normalizeSchema(schema, schemaDefs);
    }

    private static Object normalizeSchemas(Map<String, Object> schemas, Map<String, Object> schemaDefs,
            Set<String> referenced) {
        Map<String, Object> result = new TreeMap<>();
        schemas.forEach((name, rawSchema) -> {
            Map<String, Object> schema = asMap(rawSchema);
            boolean leafEnum = schema.get("enum") instanceof List<?>
                    && schema.get("properties") == null && schema.get("items") == null;
            if ((!referenced.isEmpty() && !referenced.contains(name)) || leafEnum) {
                return;
            }
            result.put(name, normalizeSchema(schema, schemaDefs));
        });
        return result;
    }

    private static Map<String, Object> normalizeSchema(Map<String, Object> original,
            Map<String, Object> schemaDefs) {
        Map<String, Object> schema = asMap(resolveRef(original, schemaDefs));
        Map<String, Object> out = new TreeMap<>();
        out.put("type", schema.get("type"));
        String format = (String) schema.get("format");
        // int32/int64 is a generator representation detail for JSON numbers; all
        // string formats (email, uuid, date-time, password) remain contractual.
        out.put("format", "integer".equals(schema.get("type")) ? null : format);
        for (String key : List.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
                "multipleOf", "pattern", "minItems", "maxItems", "uniqueItems",
                "minProperties", "maxProperties", "readOnly", "writeOnly")) {
            out.put(key, schema.get(key));
        }
        Object minLength = schema.get("minLength");
        out.put("minLength", minLength instanceof Number number && number.intValue() == 0 ? null : minLength);
        out.put("maxLength", schema.get("maxLength"));

        Object enumValue = schema.get("enum");
        if (enumValue instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparing(String::valueOf));
            out.put("enum", sorted);
        }
        if (schema.containsKey("items")) {
            out.put("items", normalizeNestedSchemaToken(schema.get("items"), schemaDefs));
        }
        if (schema.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Object> normalizedProperties = new TreeMap<>();
            properties.forEach((name, value) -> normalizedProperties.put(
                    String.valueOf(name), normalizeNestedSchemaToken(value, schemaDefs)));
            out.put("properties", normalizedProperties);
        }
        if (schema.get("required") instanceof List<?> required) {
            List<Object> sorted = new ArrayList<>(required);
            sorted.sort(Comparator.comparing(String::valueOf));
            out.put("required", sorted);
        }
        if (schema.containsKey("additionalProperties")) {
            Object additional = schema.get("additionalProperties");
            out.put("additionalProperties", additional instanceof Map<?, ?>
                    ? normalizeNestedSchemaToken(additional, schemaDefs) : additional);
        }
        return out;
    }

    private static Object normalizeNestedSchemaToken(Object rawSchema, Map<String, Object> schemaDefs) {
        Map<String, Object> schema = asMap(rawSchema);
        if (schema.isEmpty()) {
            return null;
        }
        return normalizeSchema(asMap(resolveRef(schema, schemaDefs)), schemaDefs);
    }

    /** First differing path between two normalized documents, or null when equal. */
    public static String firstDifference(Object expected, Object actual, String path) {
        if (expected == null || actual == null) {
            return expected == null && actual == null ? null
                    : path + ": expected=" + expected + " got=" + actual;
        }
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            Set<Object> expectedKeys = new java.util.TreeSet<>(expectedMap.keySet());
            Set<Object> actualKeys = new java.util.TreeSet<>(actualMap.keySet());
            if (!expectedKeys.equals(actualKeys)) {
                Set<Object> onlyExpected = new java.util.TreeSet<>(expectedKeys);
                onlyExpected.removeAll(actualKeys);
                Set<Object> onlyActual = new java.util.TreeSet<>(actualKeys);
                onlyActual.removeAll(expectedKeys);
                return path + ": key set differs (only expected=" + onlyExpected
                        + " only actual=" + onlyActual + ")";
            }
            for (Object key : expectedKeys) {
                String difference = firstDifference(expectedMap.get(key), actualMap.get(key), path + "." + key);
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) {
                return path + ": list size differs expected=" + expectedList.size()
                        + " got=" + actualList.size();
            }
            for (int i = 0; i < expectedList.size(); i++) {
                String difference = firstDifference(expectedList.get(i), actualList.get(i), path + "[" + i + "]");
                if (difference != null) {
                    return difference;
                }
            }
            return null;
        }
        return expected.equals(actual) ? null : path + ": expected=" + expected + " got=" + actual;
    }
}
