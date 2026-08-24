package com.ttg.devknowledgeplatform.content.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.ContentItemTagRepository;
import com.ttg.devknowledgeplatform.content.repository.TagRepository;
import com.ttg.devknowledgeplatform.content.repository.spec.TagSpecification;
import com.ttg.devknowledgeplatform.content.service.TagService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final ContentItemTagRepository contentItemTagRepository;
    private final SlugService slugService;

    @Override
    public Tag create(String name, TagStatus status) {
        String normalizedName = normalizeName(name);
        Validator.isFalse(tagRepository.existsByNameIgnoreCase(normalizedName), ContentErrorCode.TAG_NAME_CONFLICT, normalizedName);
        String slug = slugService.generateUniqueSlug(normalizedName, tagRepository::existsBySlug, ContentErrorCode.TAG_SLUG_CONFLICT);

        Tag tag = new Tag();
        tag.setName(normalizedName);
        tag.setSlug(slug);
        tag.setStatus(status != null ? status : TagStatus.ACTIVE);

        Tag saved = tagRepository.save(tag);
        log.info("Created tag id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public Tag update(Integer id, String name, TagStatus status) {
        Tag tag = findById(id);
        String normalizedName = normalizeName(name);

        if (!tag.getName().equalsIgnoreCase(normalizedName)) {
            Validator.isFalse(tagRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id), ContentErrorCode.TAG_NAME_CONFLICT, normalizedName);
            tag.setName(normalizedName);
            tag.setSlug(slugService.generateUniqueSlug(normalizedName, tagRepository::existsBySlugAndIdNot, id, ContentErrorCode.TAG_SLUG_CONFLICT));
        }

        if (status != null) {
            tag.setStatus(status);
        }

        Tag updated = tagRepository.save(tag);
        log.info("Updated tag id={}", id);
        return updated;
    }

    @Override
    public Tag getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<Tag> list(Pageable pageable, TagStatus status, String q) {
        Specification<Tag> spec = TagSpecification.withFilters(status, q);
        return tagRepository.findAll(spec, pageable);
    }

    @Override
    public void delete(Integer id) {
        Tag tag = findById(id);
        Validator.isFalse(contentItemTagRepository.existsByTagId(id), ContentErrorCode.TAG_IN_USE, id);
        tagRepository.delete(tag);
        log.info("Deleted tag id={}", id);
    }

    private Tag findById(Integer id) {
        return Validator.notFound(tagRepository.findById(id), ContentErrorCode.TAG_NOT_FOUND, id);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

}
