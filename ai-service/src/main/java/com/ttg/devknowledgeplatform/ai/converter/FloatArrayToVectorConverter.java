package com.ttg.devknowledgeplatform.ai.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

/**
 * Maps {@code float[]} (Java) ↔ pgvector's {@code vector} SQL type.
 *
 * pgvector accepts the string format {@code [x,y,z,...]} via an implicit
 * cast from {@code varchar} → {@code vector}, and returns it the same way
 * when read back via JDBC as a String.
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
