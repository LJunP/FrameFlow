package com.frameflow.product.infrastructure.persistence;

import org.postgresql.util.PGobject;

/** JSONB helpers: wrap string JSON in a PostgreSQL jsonb PGobject. */
public final class Jsonb {

    private Jsonb() {
    }

    public static PGobject pg(String json) {
        try {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(json == null ? "{}" : json);
            return obj;
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static PGobject pgOrDefault(String json, String fallback) {
        return pg(json == null || json.isBlank() ? fallback : json);
    }
}
