package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SlugService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SLUG_ATTEMPTS = 100;

    private final ContentItemRepository contentItemRepository;
    private final TagRepository tagRepository;

    public String generateUniqueSlug(String title) {
        String baseSlug = toSlug(title);
        String slug = baseSlug;
        int counter = 1;
        while (contentItemRepository.existsBySlug(slug)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(ErrorCode.INTERVIEW_QUESTION_SLUG_CONFLICT,
                        "Unable to generate unique slug for title: " + title);
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    // Generates a unique slug while treating the current content item's slug as available
    public String generateUniqueSlug(String title, Integer excludeContentItemId) {
        String baseSlug = toSlug(title);
        String slug = baseSlug;
        int counter = 1;
        while (contentItemRepository.existsBySlugAndIdNot(slug, excludeContentItemId)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(ErrorCode.INTERVIEW_QUESTION_SLUG_CONFLICT,
                        "Unable to generate unique slug for title: " + title);
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    public String generateUniqueTagSlug(String name) {
        String baseSlug = toSlug(name);
        String slug = baseSlug;
        int counter = 1;
        while (tagRepository.existsBySlug(slug)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(ErrorCode.TAG_SLUG_CONFLICT,
                        "Unable to generate unique slug for tag name: " + name);
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    public String generateUniqueTagSlug(String name, Integer excludeTagId) {
        String baseSlug = toSlug(name);
        String slug = baseSlug;
        int counter = 1;
        while (tagRepository.existsBySlugAndIdNot(slug, excludeTagId)) {
            if (counter > MAX_SLUG_ATTEMPTS) {
                throw new ApiException(ErrorCode.TAG_SLUG_CONFLICT,
                        "Unable to generate unique slug for tag name: " + name);
            }
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return NON_ALPHANUMERIC
                .matcher(normalized.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+|-+$", "");
    }
}