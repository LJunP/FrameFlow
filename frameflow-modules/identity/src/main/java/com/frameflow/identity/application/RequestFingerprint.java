package com.frameflow.identity.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Canonical method+path+payload fingerprint (SHA-256 hex) used by request dedup. */
@Component
public class RequestFingerprint {

    private final ObjectMapper mapper;

    public RequestFingerprint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String compute(String method, String path, Object payload) {
        String canonical = canonicalJson(payload);
        return sha256Hex(method + "|" + path + "|" + canonical);
    }

    private String canonicalJson(Object payload) {
        try {
            JsonNode tree = mapper.valueToTree(payload);
            ObjectMapper sorted = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return sorted.writeValueAsString(sort(tree));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to canonicalize request payload", e);
        }
    }

    private Object sort(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Map<String, Object> map = new TreeMap<>();
            objectNode.fields().forEachRemaining(entry -> map.put(entry.getKey(), sort(entry.getValue())));
            return map;
        }
        if (node instanceof ArrayNode arrayNode) {
            List<Object> list = new ArrayList<>();
            arrayNode.forEach(item -> list.add(sort(item)));
            return list;
        }
        return node;
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
