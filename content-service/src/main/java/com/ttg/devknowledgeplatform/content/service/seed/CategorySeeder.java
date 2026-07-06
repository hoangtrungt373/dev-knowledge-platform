package com.ttg.devknowledgeplatform.content.service.seed;

import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Seeds {@link Category} rows from {@code data/csv/categories.csv} (columns: id, name,
 * parentId). Rows must list parent categories before their children — a child's parent is
 * resolved by looking up an already-persisted category by {@code seedId} in the same pass.
 *
 * <p>{@code id} is a permanent, seed-file-only identifier (persisted as {@code Category.seedId},
 * never shown to end users) — the sole idempotency key, and also what {@code parentId} and
 * {@code QuestionAnswer}'s {@code categoryId} reference it by. It's deliberately decoupled
 * from {@code name}/{@code slug} so those stay freely editable across re-runs — and so every
 * cross-reference to this category stays valid — without risking a duplicate insert or a broken
 * reference: this seed data is long-lived and coexists permanently with user-created categories,
 * not throwaway dev data that gets wiped and reseeded. Referencing (or identifying) a category
 * by {@code name} instead, as an earlier version of this mechanism did, would break the moment
 * that category's display name was edited — either inserting a duplicate row, or leaving every
 * file that referenced the old name pointing at nothing.
 *
 * <p>{@code slug} itself is never authored; it's generated via
 * {@link SlugService#generateUniqueSlug} with the production {@link ContentErrorCode#CATEGORY_SLUG_CONFLICT},
 * the same call {@code CategoryServiceImpl.create()} makes.
 *
 * @author ttg
 */
@Component
@RequiredArgsConstructor
public class CategorySeeder extends CsvSeeder<Category> {

    private final CategoryRepository categoryRepository;
    private final SlugService slugService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/categories.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        String seedId = record.get("id");
        String name = record.get("name");
        return categoryRepository.findBySeedId(seedId)
                .map(existing -> {
                    if (!existing.getName().equalsIgnoreCase(name)) {
                        throw new IllegalStateException("categories.csv id '" + seedId
                                + "' is already used by category '" + existing.getName()
                                + "' but this row now has name '" + name
                                + "' — an id must never be reused for a different category");
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    protected Category buildEntity(CSVRecord record) {
        String seedId = record.get("id");
        String name = record.get("name");
        String parentId = record.get("parentId");

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("categories.csv id '" + seedId + "' has name '" + name
                    + "', but a category with that name already exists (created by a different id, or by a user) — "
                    + "rename one of them to resolve the conflict");
        }

        Category parent = null;
        if (parentId != null && !parentId.isBlank()) {
            parent = categoryRepository.findBySeedId(parentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "categories.csv references unknown parentId '" + parentId
                                    + "' — parent rows must appear before their children"));
        }

        Category category = new Category();
        category.setSeedId(seedId);
        category.setName(name);
        category.setSlug(slugService.generateUniqueSlug(name, categoryRepository::existsBySlug, ContentErrorCode.CATEGORY_SLUG_CONFLICT));
        category.setParent(parent);
        return category;
    }

    @Override
    protected void persist(Category entity) {
        categoryRepository.save(entity);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("id");
    }
}
