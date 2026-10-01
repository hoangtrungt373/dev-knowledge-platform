package com.ttg.devknowledgeplatform.devpractice.service.seed;

import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemTagService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvSeeder;

import lombok.RequiredArgsConstructor;

/**
 * Seeds the starter topic catalog (Array, Stack, Dynamic Programming, ...) from
 * {@code data/csv/problem_tags.csv} — a single {@code name} column. Same shape as
 * {@code ecommerce-service}'s {@code ProductTagSeeder}, built on {@code infra}'s {@link CsvSeeder}
 * Template Method (this class supplies only the per-row steps).
 *
 * <p>Idempotent by name, case-insensitively — the same uniqueness rule the tag table enforces — so a
 * tag an admin has renamed counts as gone and is re-seeded under its old name, and a deleted one comes
 * back on the next seeded startup. That's the accepted trade-off of seeding a small starter list at
 * startup rather than once in a migration.
 *
 * <p>Unlike {@code ProductTagSeeder}, each row goes through {@link ProblemTagService#create} rather
 * than the repository — same choice as {@link ProblemSeeder}: one write path, so seeded tags are
 * trimmed, uniqueness-checked and slugged exactly like admin-created ones. The "entity" this seeder
 * builds is therefore just the tag name.
 */
@Component
@RequiredArgsConstructor
public class ProblemTagSeeder extends CsvSeeder<String> {

    private final ProblemTagRepository problemTagRepository;
    private final ProblemTagService problemTagService;

    @Override
    protected String csvClasspathLocation() {
        return "data/csv/problem_tags.csv";
    }

    @Override
    protected boolean alreadyExists(CSVRecord record) {
        return problemTagRepository.existsByNameIgnoreCase(record.get("name").trim());
    }

    @Override
    protected String buildEntity(CSVRecord record) {
        return record.get("name");
    }

    @Override
    protected void persist(String name) {
        problemTagService.create(name);
    }

    @Override
    protected String naturalKey(CSVRecord record) {
        return record.get("name");
    }
}
