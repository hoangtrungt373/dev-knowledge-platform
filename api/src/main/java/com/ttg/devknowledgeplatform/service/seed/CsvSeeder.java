package com.ttg.devknowledgeplatform.service.seed;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Template Method skeleton for idempotent startup data seeding from a classpath CSV file.
 * The read-parse-insert-or-skip algorithm is identical for every flat, single-file seed source
 * (see {@link CategorySeeder}, {@link TagSeeder}); subclasses supply only the natural-key
 * existence check and the entity construction. Seed sources with a different shape — e.g.
 * {@link QuestionAnswerSeeder}'s one-file-per-record Markdown format — implement their own
 * {@code seed()} rather than forcing that shape through this template.
 *
 * @param <T> the entity (or per-row composite holder) built from each CSV row
 * @author ttg
 */
@Slf4j
public abstract class CsvSeeder<T> {

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
    public final int seed() {
        int inserted = 0;
        int skipped = 0;
        ClassPathResource resource = new ClassPathResource(csvClasspathLocation());
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (InputStream in = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                if (alreadyExists(record)) {
                    skipped++;
                    log.debug("{}: skipping existing row '{}'", getClass().getSimpleName(), naturalKey(record));
                    continue;
                }
                persist(buildEntity(record));
                inserted++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed CSV: " + csvClasspathLocation(), e);
        }

        log.info("{}: inserted {} row(s), skipped {} already-present row(s)",
                getClass().getSimpleName(), inserted, skipped);
        return inserted;
    }
}
