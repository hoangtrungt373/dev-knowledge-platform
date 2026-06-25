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
