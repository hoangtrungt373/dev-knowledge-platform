package com.ttg.devknowledgeplatform.infra.service.seed;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The classpath-CSV-reading step behind every seeder in this reactor — {@link CsvSeeder}'s own
 * {@code seed()} uses this internally, and any seeder whose per-row shape doesn't fit that
 * template (joins two files before persisting anything, or must gather every row for one key
 * before persisting — e.g. {@code ecommerce-service}'s own {@code ProductSeeder}/
 * {@code ProductCategoryAttributeSeeder}) can call it directly instead of hand-rolling the
 * identical {@code CSVFormat}/try-with-resources boilerplate, which is exactly what both of those
 * classes used to do independently before this extraction (a code-quality-audit finding — each
 * carried a byte-identical private {@code readCsv} method, justified at the time by
 * {@link CsvSeeder#seed()} being {@code final} and not fitting either seeder's multi-row shape;
 * that reasoning only ever argued against reusing {@code seed()} itself, never against sharing the
 * read step underneath it).
 */
public final class CsvReader {

    private CsvReader() {
    }

    /**
     * Reads every data row (header row skipped) from a classpath CSV file, trimming cell values and
     * ignoring surrounding whitespace — the one {@code CSVFormat} convention every seeder in this
     * reactor shares.
     *
     * @throws IllegalStateException if the file can't be read (missing from the classpath, malformed)
     */
    public static List<CSVRecord> readAll(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();
        try (InputStream in = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed CSV: " + classpathLocation, e);
        }
    }
}
