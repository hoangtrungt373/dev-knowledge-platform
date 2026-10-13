package com.ttg.devknowledgeplatform.devpractice.service.seed;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlanSection;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.StudyPlanRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds study plans from {@code data/study-plans/*.yml} — one file per plan (see
 * {@code neetcode-150.yml} for the format): a name, an optional description, an overview position,
 * and ordered sections listing problem slugs in study order.
 *
 * <p><b>Idempotent by slug</b> (derived from the name, unique): a plan that already exists is skipped
 * whole, never merged — so editing a seeded plan's file has no effect on a database that already has
 * it. That's the accepted cost of seed-only plans until admin management exists.
 *
 * <p><b>Fails loudly, naming the file</b>, on a missing field, an unknown problem slug, or a problem
 * listed twice in one plan — a typo in seed data should stop startup, not quietly drop a problem.
 * Problems are referenced in any status; only published ones are shown publicly.
 *
 * <p>Writes through the repository: there's no study-plan write service yet, so the validation above
 * lives here. Must run after {@link ProblemSeeder}, whose problems it references.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyPlanSeeder implements Seeder {

    static final String PLANS_LOCATION = "classpath*:data/study-plans/*.yml";

    private final StudyPlanRepository studyPlanRepository;
    private final ProblemRepository problemRepository;
    private final SlugService slugService;

    // SafeConstructor: plain maps/lists/scalars only, same as ProblemSeeder.
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    /**
     * Seeds every plan file whose slug isn't already present.
     *
     * @return the number of plans created
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public int seed() {
        return seed(PLANS_LOCATION);
    }

    /** Seeds from {@code location} — split out so tests can point at their own files. */
    int seed(String location) {
        int created = 0;
        for (Resource file : resources(location)) {
            String fileName = file.getFilename();
            Map<String, Object> data = load(file);
            String name = requiredString(data, "name", fileName);
            String slug = slugService.toSlug(name);
            if (studyPlanRepository.existsBySlug(slug)) {
                log.debug("StudyPlanSeeder: '{}' already exists, skipping", name);
                continue;
            }
            StudyPlan plan = studyPlanRepository.save(toPlan(data, name, slug, fileName));
            created++;
            log.info("StudyPlanSeeder: created plan '{}' with {} section(s)", plan.getName(), plan.getSections().size());
        }
        log.info("StudyPlanSeeder: {} plan(s) created", created);
        return created;
    }

    private StudyPlan toPlan(Map<String, Object> data, String name, String slug, String fileName) {
        StudyPlan plan = StudyPlan.builder()
                .name(name)
                .slug(slug)
                .description(optionalString(data, "description"))
                .position(requiredInt(data, "position", fileName))
                .build();

        Set<String> seenProblems = new HashSet<>();
        for (Map<String, Object> sectionData : requiredList(data, "sections", fileName)) {
            StudyPlanSection section = plan.addSection(
                    requiredString(sectionData, "title", fileName), optionalString(sectionData, "description"));
            for (Object problemSlug : requiredRawList(sectionData, "problems", fileName)) {
                String problemKey = String.valueOf(problemSlug).trim();
                if (!seenProblems.add(problemKey)) {
                    throw new IllegalStateException(fileName + ": problem '" + problemKey + "' is listed more than once");
                }
                Problem problem = problemRepository.findBySlug(problemKey).orElseThrow(() ->
                        new IllegalStateException(fileName + ": no problem with slug '" + problemKey
                                + "' (see data/problems/*.md — ProblemSeeder must have created it first)"));
                section.addProblem(problem);
            }
        }
        return plan;
    }

    private static Resource[] resources(String location) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list study plan seed files: " + location, e);
        }
    }

    private Map<String, Object> load(Resource file) {
        try (InputStream in = file.getInputStream()) {
            Object parsed = yaml.load(in);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalStateException(file.getFilename() + ": expected a YAML mapping at the top level");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read study plan seed file " + file.getFilename(), e);
        }
    }

    private static String requiredString(Map<String, Object> data, String key, String fileName) {
        String value = optionalString(data, key);
        if (value == null) {
            throw new IllegalStateException(fileName + ": '" + key + "' is required");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? null : text;
    }

    private static int requiredInt(Map<String, Object> data, String key, String fileName) {
        if (data.get(key) instanceof Integer value) {
            return value;
        }
        throw new IllegalStateException(fileName + ": '" + key + "' must be a whole number");
    }

    private static List<Object> requiredRawList(Map<String, Object> data, String key, String fileName) {
        if (data.get(key) instanceof List<?> list && !list.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) list;
            return items;
        }
        throw new IllegalStateException(fileName + ": '" + key + "' must be a non-empty list");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requiredList(Map<String, Object> data, String key, String fileName) {
        List<Object> items = requiredRawList(data, key, fileName);
        for (Object item : items) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalStateException(fileName + ": every entry of '" + key + "' must be a mapping");
            }
        }
        return (List<Map<String, Object>>) (List<?>) items;
    }
}
