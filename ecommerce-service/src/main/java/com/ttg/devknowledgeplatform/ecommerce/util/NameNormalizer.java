package com.ttg.devknowledgeplatform.ecommerce.util;

/**
 * Trims a name before a case-insensitive-existence check and persist — shared by
 * {@code ProductCategoryServiceImpl}, {@code ProductTagServiceImpl}, and
 * {@code ProductAttributeServiceImpl}'s own {@code create}/{@code update}, which each carried a
 * byte-identical private {@code normalizeName} method (a null-safe {@code trim()}) before this
 * extraction — a code-quality-audit finding.
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim();
    }
}
