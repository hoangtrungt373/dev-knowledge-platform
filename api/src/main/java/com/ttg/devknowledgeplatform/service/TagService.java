package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.admin.TagResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateTagRequest;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of content tags.
 *
 * <p>Tag names are case-insensitively unique. Each tag automatically receives a
 * URL-safe slug derived from its name. A tag cannot be deleted while it is still
 * referenced by any content item.
 */
public interface TagService {

    /**
     * Creates a new tag.
     *
     * @param request the tag name and optional initial status
     * @return the created tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if a tag with the same name already exists
     */
    TagResponse create(CreateTagRequest request);

    /**
     * Updates the name and/or status of an existing tag.
     *
     * <p>The slug is regenerated only when the name changes.
     *
     * @param id      the tag's primary key
     * @param request the fields to update
     * @return the updated tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no tag exists with {@code id}
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another tag
     */
    TagResponse update(Integer id, UpdateTagRequest request);

    /**
     * Returns a single tag by its primary key.
     *
     * @param id the tag's primary key
     * @return the matching tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    TagResponse getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of tags.
     *
     * @param pageable pagination and sort parameters
     * @param status   filter by {@link TagStatus}; {@code null} returns all statuses
     * @param q        case-insensitive name substring filter; {@code null} or blank returns all
     * @return a page of matching tags
     */
    PagedResponse<TagResponse> list(Pageable pageable, TagStatus status, String q);

    /**
     * Permanently deletes a tag.
     *
     * @param id the tag's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the tag is still in use by content items
     */
    void delete(Integer id);
}
