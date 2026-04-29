package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.entity.Tag;
import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.admin.TagResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateTagRequest;
import com.ttg.devknowledgeplatform.repository.TagRepository;
import com.ttg.devknowledgeplatform.repository.spec.TagSpecification;
import com.ttg.devknowledgeplatform.service.SlugService;
import com.ttg.devknowledgeplatform.service.TagService;
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
@Transactional
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final SlugService slugService;

    @Override
    public TagResponse create(CreateTagRequest request) {
        String name = normalizeName(request.getName());
        if (tagRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.TAG_NAME_CONFLICT,
                    "A tag with name '" + name + "' already exists");
        }
        String slug = slugService.generateUniqueTagSlug(name);

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slug);
        tag.setStatus(request.getStatus() != null ? request.getStatus() : TagStatus.ACTIVE);

        Tag saved = tagRepository.save(tag);
        log.info("Created tag id={} slug={}", saved.getId(), slug);
        return toResponse(saved);
    }

    @Override
    public TagResponse update(Integer id, UpdateTagRequest request) {
        Tag tag = findById(id);
        String name = normalizeName(request.getName());

        if (!tag.getName().equalsIgnoreCase(name)) {
            if (tagRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new ApiException(ErrorCode.TAG_NAME_CONFLICT,
                        "A tag with name '" + name + "' already exists");
            }
            tag.setName(name);
            tag.setSlug(slugService.generateUniqueTagSlug(name, id));
        }

        if (request.getStatus() != null) {
            tag.setStatus(request.getStatus());
        }

        Tag updated = tagRepository.save(tag);
        log.info("Updated tag id={}", id);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse getById(Integer id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TagResponse> list(Pageable pageable, TagStatus status, String q) {
        Specification<Tag> spec = TagSpecification.withFilters(status, q);
        Page<TagResponse> page = tagRepository.findAll(spec, pageable).map(this::toResponse);
        return PagedResponse.from(page);
    }

    private Tag findById(Integer id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TAG_NOT_FOUND,
                        "Tag not found with id: " + id));
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    private TagResponse toResponse(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .status(tag.getStatus())
                .createdAt(tag.getDteCreation())
                .updatedAt(tag.getDteLastModification())
                .build();
    }
}
