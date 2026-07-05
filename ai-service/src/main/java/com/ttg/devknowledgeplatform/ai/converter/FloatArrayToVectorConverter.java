package com.ttg.devknowledgeplatform.ai.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

/**
 * Maps {@code float[]} (Java) ↔ pgvector's {@code vector} SQL type, using the string format
 * {@code [x,y,z,...]} pgvector's input/output functions accept.
 *
 * <p>This converter alone is not sufficient for writes: a JDBC prepared-statement parameter
 * bound as a plain {@code String} is sent to PostgreSQL typed as {@code varchar}, which does
 * <strong>not</strong> implicitly cast to {@code vector} the way a string literal written
 * directly in SQL text does — that mismatch fails with "column is of type vector but expression
 * is of type character varying". Every entity field using this converter must also declare
 * {@code @org.hibernate.annotations.JdbcType(PgVectorJdbcType.class)} (see
 * {@code ContentEmbedding.embedding}) so Hibernate binds the parameter via
 * {@code setObject(index, value, Types.OTHER)} instead of {@code setString(...)}, letting
 * Postgres resolve the value's type from the target column instead of the bind's declared type.
 * {@code @JdbcTypeCode(SqlTypes.OTHER)} looks like the obvious shortcut but does <strong>not</strong>
 * work here — see {@link PgVectorJdbcType}'s javadoc for why a custom {@code JdbcType} is needed
 * instead. Reads are unaffected — pgvector always returns the value as a String regardless of
 * how it was written.
 */
@Converter
public class FloatArrayToVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : attribute) joiner.add(String.valueOf(f));
        return joiner.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String inner = dbData.trim();
        inner = inner.substring(1, inner.length() - 1);
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
