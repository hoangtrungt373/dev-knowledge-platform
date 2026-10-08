package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemTagService;
import com.ttg.devknowledgeplatform.infra.service.seed.CsvReader;

/** Runs the real {@code data/csv/problem_tags.csv} through {@link ProblemTagSeeder}. */
class ProblemTagSeederTest {

    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemTagService tagService = mock(ProblemTagService.class);
    private final ProblemTagSeeder seeder = new ProblemTagSeeder(tagRepository, tagService);

    @Test
    void createsEveryTagThatDoesNotExistYetThroughTheService() {
        when(tagRepository.existsByNameIgnoreCase("Array")).thenReturn(true);

        assertThat(seeder.seed()).isEqualTo(18);

        verify(tagService, never()).create("Array");
        verify(tagService).create("Dynamic Programming");
        verify(tagService, times(18)).create(anyString());
    }

    @Test
    void theCsvHasOnlyDistinctNonBlankNames() {
        List<String> names = csvNames();

        assertThat(names).hasSize(19).allMatch(n -> !n.isBlank());
        assertThat(names.stream().map(n -> n.toLowerCase(Locale.ROOT)).distinct()).hasSize(19);
    }

    /** The CSV's tag names — also what {@link ProblemSeederTest} resolves seed problems' tags against. */
    static List<String> csvNames() {
        return CsvReader.readAll("data/csv/problem_tags.csv").stream().map((CSVRecord r) -> r.get("name").trim()).toList();
    }
}
