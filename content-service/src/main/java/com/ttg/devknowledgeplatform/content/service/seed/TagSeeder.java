package com.ttg.devknowledgeplatform.content.service.seed;

import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.TagRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Seeds {@link Tag} rows from {@code data/csv/tags.csv} (columns: id, name, status). An empty
 * status column defaults to {@link TagStatus#ACTIVE}.
 *
 * <p>{@code id} is a permanent, seed-file-only identifier (persisted as {@code Tag.seedId},
 * never shown to end users) — the sole idempotency key, deliberately decoupled from
 * {@code name}/{@code slug} for the same reason as {@link CategorySeeder}: this seed data is
 * long-lived and coexists permanently with user-created tags, so a tag's display name must stay
 * freely editable without risking a duplicate insert on the next seeding run.
 *
 * <p>{@code slug} itself is never authored; it's generated via
 * {@link SlugService#generateUniqueSlug} with the production {@link ContentErrorCode#TAG_SLUG_CONFLICT},
 * the same call {@code TagServiceImpl.create()} makes.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class TagSeeder extends CsvSeeder<Tag> {

    private final TagRepository tagRepository;
    private final SlugService slugService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/tags.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        String seedId = record.get("id");
        String name = record.get("name");
        return tagRepository.findBySeedId(seedId)
                .map(existing -> {
                    if (!existing.getName().equalsIgnoreCase(name)) {
                        throw new IllegalStateException("tags.csv id '" + seedId
                                + "' is already used by tag '" + existing.getName()
                                + "' but this row now has name '" + name
                                + "' — an id must never be reused for a different tag");
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    protected Tag buildEntity(CSVRecord record) {
        String seedId = record.get("id");
        String name = record.get("name");
        String status = record.get("status");

        if (tagRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("tags.csv id '" + seedId + "' has name '" + name
                    + "', but a tag with that name already exists (created by a different id, or by a user) — "
                    + "rename one of them to resolve the conflict");
        }

        Tag tag = new Tag();
        tag.setSeedId(seedId);
        tag.setName(name);
        tag.setSlug(slugService.generateUniqueSlug(name, tagRepository::existsBySlug, ContentErrorCode.TAG_SLUG_CONFLICT));
        tag.setStatus(status == null || status.isBlank()
                ? TagStatus.ACTIVE
                : TagStatus.valueOf(status.trim().toUpperCase()));
        return tag;
    }

    @Override
    protected void persist(Tag entity) {
        tagRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("id");
    }
}
