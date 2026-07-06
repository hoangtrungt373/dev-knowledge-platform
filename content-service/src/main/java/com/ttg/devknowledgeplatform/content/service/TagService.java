package com.ttg.devknowledgeplatform.content.service;

import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Manages the lifecycle of content tags.
 *
 * <p>Tag names are case-insensitively unique. Each tag automatically receives a
 * URL-safe slug derived from its name. A tag cannot be deleted while it is still
 * referenced by any content item.
 *
 * <p>Returns entities rather than REST DTOs — {@code api}'s {@code TagMapper} does the
 * entity-to-response mapping, matching how {@code social-service}'s {@code FriendService} and
 * {@code ai-service}'s {@code RagQueryService} return internal models that {@code api} maps to
 * its own response types.
 */
public interface TagService {

    /**
     * Creates a new tag.
     *
     * @param name   the tag name
     * @param status the initial status, or {@code null} to default to {@link TagStatus#ACTIVE}
     * @return the created tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if a tag with the same name already exists
     */
    Tag create(String name, TagStatus status);

    /**
     * Updates the name and/or status of an existing tag.
     *
     * <p>The slug is regenerated only when the name changes.
     *
     * @param id     the tag's primary key
     * @param name   the new name
     * @param status the new status, or {@code null} to leave it unchanged
     * @return the updated tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if no tag exists with {@code id}
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the new name conflicts with another tag
     */
    Tag update(Integer id, String name, TagStatus status);

    /**
     * Returns a single tag by its primary key.
     *
     * @param id the tag's primary key
     * @return the matching tag
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     */
    Tag getById(Integer id);

    /**
     * Returns a paginated, optionally filtered list of tags.
     *
     * @param pageable pagination and sort parameters
     * @param status   filter by {@link TagStatus}; {@code null} returns all statuses
     * @param q        case-insensitive name substring filter; {@code null} or blank returns all
     * @return a page of matching tags
     */
    Page<Tag> list(Pageable pageable, TagStatus status, String q);

    /**
     * Permanently deletes a tag.
     *
     * @param id the tag's primary key
     * @throws com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException if not found
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the tag is still in use by content items
     */
    void delete(Integer id);
}
