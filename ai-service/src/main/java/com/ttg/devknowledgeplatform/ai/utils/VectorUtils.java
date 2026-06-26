package com.ttg.devknowledgeplatform.ai.utils;

import java.util.StringJoiner;

/**
 * Shared vector math utilities used by pipeline stages.
 */
public class VectorUtils {

    private VectorUtils() {}

    /**
     * Computes cosine similarity between two L2-normalized embedding vectors via dot product.
     *
     * <p>OpenAI's text-embedding models return L2-normalized vectors, so
     * {@code cosine_similarity(a, b) = dot_product(a, b)} — no division required.
     *
     * @return similarity in {@code [0, 1]}; {@code 1.0} = identical direction, {@code 0.0} = orthogonal
     */
    public static float dotProduct(float[] a, float[] b) {
        float sum = 0f;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Parses a pgvector text literal {@code [f1,f2,...,fn]} back into a {@code float[]}.
     *
     * <p>Inverse of {@link #toVectorString(float[])}. Used by {@code CorpusStatisticsService}
     * to deserialise centroid vectors loaded from {@code SYS_PARAM.VALUE}.
     *
     * @param vectorText pgvector text notation, e.g. {@code [0.12,-0.03,0.41]}
     * @return the parsed float array
     * @throws NumberFormatException if any element cannot be parsed as a float
     */
    public static float[] parseVector(String vectorText) {
        String stripped = vectorText.strip();
        if (stripped.startsWith("[")) stripped = stripped.substring(1);
        if (stripped.endsWith("]"))   stripped = stripped.substring(0, stripped.length() - 1);
        String[] parts = stripped.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].strip());
        }
        return result;
    }

    /**
     * Returns an L2-normalised copy of {@code v}.
     *
     * <p>Used to normalise corpus centroid vectors before caching them.
     * The centroid is computed via SQL {@code avg(embedding)} — averaging unit vectors
     * yields a shorter vector whose L2 norm is less than 1, so a raw dot product with a
     * query embedding would not equal cosine similarity. Normalising restores that property.
     *
     * <p>Returns a zero vector unchanged to avoid division by zero on an empty corpus.
     *
     * @param v the vector to normalise (not modified)
     * @return a new array with the same direction but unit length, or {@code v} itself if all-zero
     */
    public static float[] normalize(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        if (norm == 0f) return v;
        norm = (float) Math.sqrt(norm);
        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) result[i] = v[i] / norm;
        return result;
    }

    /**
     * Formats a float array as the pgvector text literal {@code [x,y,z,...]}.
     *
     * <p>Required by the native {@code CAST(:embedding AS vector)} expression used in
     * {@code ContentEmbeddingRepository.findTopSimilarIds}.
     */
    public static String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : embedding) {
            joiner.add(String.valueOf(f));
        }
        return joiner.toString();
    }
}
