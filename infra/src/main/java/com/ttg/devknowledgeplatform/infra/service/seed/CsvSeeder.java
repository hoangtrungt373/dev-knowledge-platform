package com.ttg.devknowledgeplatform.infra.service.seed;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;

/**
 * Template Method skeleton for idempotent startup data seeding from a classpath CSV file.
 * The read-parse-insert-or-skip algorithm is identical for every flat, single-file seed source
 * (e.g. {@code content-service}'s {@code CategorySeeder}/{@code TagSeeder}, {@code api}'s
 * {@code UserSeeder}, {@code social-service}'s {@code UserBlockSeeder}); subclasses supply only
 * the natural-key existence check and the entity construction. Seed sources with a different
 * shape — e.g. {@code QuestionAnswerSeeder}'s one-file-per-record Markdown format, or
 * {@code FriendGraphSeeder}'s one-row-produces-two-entities shape — implement their own
 * {@code seed()} rather than forcing that shape through this template.
 *
 * <p>Lives here (not any single feature module) because it's genuinely feature-agnostic and
 * needed by more than one module that can't depend on each other ({@code content-service} and
 * {@code social-service} are independent siblings) — the same reasoning behind
 * {@code SlugService} living in {@code infra}.
 *
 * @param <T> the entity (or per-row composite holder) built from each CSV row
 * @author ttg
 */
@Slf4j
public abstract class CsvSeeder<T> implements Seeder {

    /** Classpath-relative location of the CSV file, e.g. {@code data/csv/categories.csv}. */
    protected abstract String csvClasspathLocation();

    /** Natural-key existence check for one row; returning {@code true} skips it (idempotency). */
    protected abstract boolean alreadyExists(CSVRecord record);

    /** Builds the entity (or composite holder) from a CSV row. Must not persist anything. */
    protected abstract T buildEntity(CSVRecord record);

    /** Persists the entity built by {@link #buildEntity}. */
    protected abstract void persist(T entity);

    /** Natural key used in skip-log lines, e.g. the row's slug. */
    protected abstract String naturalKey(CSVRecord record);

    /**
     * Reads the CSV file and inserts every row whose natural key is not already present.
     *
     * @return the number of rows inserted
     */
    @Override
    public final int seed() {
        int inserted = 0;
        int skipped = 0;
        for (CSVRecord record : CsvReader.readAll(csvClasspathLocation())) {
            if (alreadyExists(record)) {
                skipped++;
                log.debug("{}: skipping existing row '{}'", getClass().getSimpleName(), naturalKey(record));
                continue;
            }
            persist(buildEntity(record));
            inserted++;
        }

        log.info("{}: inserted {} row(s), skipped {} already-present row(s)",
                getClass().getSimpleName(), inserted, skipped);
        return inserted;
    }
}
