package com.ttg.devknowledgeplatform.infra.service.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

@Service
@Transactional(rollbackFor = Throwable.class)
public class SlugServiceImpl implements SlugService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SLUG_ATTEMPTS = 100;

    @Override
    public String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return NON_ALPHANUMERIC
                .matcher(normalized.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+|-+$", "");
    }

    @Override
    public String generateUniqueSlug(String input, Predicate<String> exists, ErrorCode conflictCode) {
        String baseSlug = toSlug(input);
        String slug = baseSlug;
        int counter = 1;
        while (exists.test(slug)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(conflictCode, new Object[] {input});
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    @Override
    public String generateUniqueSlug(String input, BiPredicate<String, Integer> existsExcluding,
            Integer excludeId, ErrorCode conflictCode) {
        String baseSlug = toSlug(input);
        String slug = baseSlug;
        int counter = 1;
        while (existsExcluding.test(slug, excludeId)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(conflictCode, new Object[] {input});
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }
}
