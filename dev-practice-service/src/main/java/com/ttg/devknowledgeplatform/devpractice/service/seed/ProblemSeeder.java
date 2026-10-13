package com.ttg.devknowledgeplatform.devpractice.service.seed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;
import com.ttg.devknowledgeplatform.devpractice.harness.ParsedSignature;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureTemplateParserRegistry;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionService;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.SubmissionService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds practice problems from {@code data/problems/*.md} — one file per problem: a YAML front-matter
 * block (title, difficulty, status, tags, code template, test cases) and a Markdown body (the
 * description). Same file shape as {@code content-service}'s {@code QuestionAnswerSeeder}.
 *
 * <p><b>Goes through the real write path, not around it.</b> The signature is read from the file's
 * code template by {@link SignatureTemplateParserRegistry} (exactly what the admin form's "Parse
 * template" button does), and the problem is created by {@link ProblemService#create} — so seed data
 * passes the same identifier, reserved-name, tag and test-case-arity checks as anything an admin
 * enters, and a bad seed file fails startup with that file's name instead of saving a problem that
 * can't be judged.
 *
 * <p><b>Idempotent by slug</b> (unique, derived from the title the way {@code ProblemService} derives
 * it): a problem whose slug already exists is skipped, so re-running seeds nothing twice and never
 * overwrites a problem an admin has since edited. Renaming a seeded problem's title in its file would
 * seed it again under the new slug — change the file's {@code title} only on a fresh database.
 *
 * <p><b>Publishing goes through verification too.</b> A problem can't be created straight into
 * {@code PUBLISHED} (see {@code ProblemService#create}), so a file asking for {@code status: PUBLISHED}
 * must carry a {@code referenceSolution}: the problem is created as a {@code DRAFT}, the solution is
 * submitted as a publish-on-accept REFERENCE run, and the judge publishes it once that run is
 * {@code ACCEPTED} — asynchronously, a few seconds after startup. If the judge rejects it or is
 * unreachable (e.g. no RapidAPI key), the problem simply stays a draft and the run shows up in the
 * admin form's reference panel; since seeding is idempotent by slug, it isn't retried on the next
 * startup. A {@code referenceSolution} on a {@code DRAFT} file verifies it without publishing.
 *
 * <p><b>Editorial solutions</b> come from an optional {@code solutions:} list, in display order: each
 * approach has a {@code title}, a Markdown {@code explanation}, and its code — either
 * {@code useReferenceSolution: true} (link the {@code referenceSolution} run above, so its code isn't
 * written twice) and/or {@code implementations:} ({@code language}/{@code code} pairs, each submitted
 * as its own REFERENCE run, never publish-on-accept). Created through
 * {@link ProblemSolutionService#create}, the same validated path admin management will use. Links are
 * made before the judge has answered; learners only ever see accepted runs' code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemSeeder implements Seeder {

    /** Seeded problems have no human author; a fixed, recognizable placeholder subject id. */
    static final String SEED_AUTHOR_UUID = "00000000-0000-0000-0000-000000000000";

    private static final String PROBLEMS_LOCATION = "classpath*:data/problems/*.md";
    private static final Pattern FRONT_MATTER_DELIMITER = Pattern.compile("(?m)^---\\s*$");

    private final ProblemRepository problemRepository;
    private final ProblemTagRepository problemTagRepository;
    private final ProblemService problemService;
    private final SubmissionService submissionService;
    private final ProblemSolutionService problemSolutionService;
    private final SignatureTemplateParserRegistry templateParsers;
    private final SlugService slugService;

    // SafeConstructor: plain YAML types only (maps, lists, scalars) — no type tags that instantiate
    // arbitrary classes, which a data file never needs.
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    /**
     * Seeds every problem file whose slug isn't already present.
     *
     * @return the number of problems created
     */
    @Override
    public int seed() {
        Resource[] files;
        try {
            files = new PathMatchingResourcePatternResolver().getResources(PROBLEMS_LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list problem seed files: " + PROBLEMS_LOCATION, e);
        }

        int created = 0;
        int skipped = 0;
        for (Resource file : files) {
            SeedProblem problem = read(file);
            if (problemRepository.existsBySlug(slugService.toSlug(problem.title()))) {
                skipped++;
                log.debug("ProblemSeeder: '{}' already exists, skipping", problem.title());
                continue;
            }
            if (problem.status() == ContentStatus.PUBLISHED && problem.referenceSolution() == null) {
                throw new IllegalStateException(file.getFilename() + ": status PUBLISHED needs a referenceSolution "
                        + "(a problem is only published once a reference solution is accepted)");
            }
            try {
                Problem saved = problemService.create(toCommand(problem), SEED_AUTHOR_UUID);
                Submission referenceRun = null;
                if (problem.referenceSolution() != null) {
                    referenceRun = submissionService.createReference(SEED_AUTHOR_UUID, new SubmissionCommands.Create(saved.getId(),
                                    problem.referenceSolution().language(), problem.referenceSolution().code()),
                            problem.status() == ContentStatus.PUBLISHED);
                }
                seedSolutions(saved.getId(), problem.solutions(), referenceRun);
            } catch (ApiException e) {
                // A seed file is developer-authored: failing startup with the file's name beats
                // half-seeding or silently skipping it.
                throw new IllegalStateException(file.getFilename() + ": " + e.getMessage(), e);
            }
            created++;
        }
        log.info("ProblemSeeder: created {} problem(s), skipped {} already present", created, skipped);
        return created;
    }

    /** Creates the file's approaches in order, each implementation as its own (non-publishing) REFERENCE run. */
    private void seedSolutions(Integer problemId, List<SeedSolution> solutions, Submission referenceRun) {
        for (SeedSolution solution : solutions) {
            List<Integer> runIds = new ArrayList<>();
            if (solution.useReferenceSolution()) {
                runIds.add(referenceRun.getId()); // presence checked by read()
            }
            for (ReferenceSolution implementation : solution.implementations()) {
                Submission run = submissionService.createReference(SEED_AUTHOR_UUID,
                        new SubmissionCommands.Create(problemId, implementation.language(), implementation.code()), false);
                runIds.add(run.getId());
            }
            problemSolutionService.create(problemId,
                    new ProblemSolutionCommands.Create(solution.title(), solution.explanation(), runIds));
        }
    }

    private ProblemCommands.Create toCommand(SeedProblem problem) {
        ParsedSignature signature = templateParsers.parse(problem.templateLanguage(), problem.templateCode());
        requireUnambiguous(signature, problem.title());

        List<ProblemCommands.MethodParameterInput> parameters = ProblemCommands.MethodParameterInput.inOrder(
                signature.parameters(), p -> p.name(), p -> p.type().type());
        // PUBLISHED is reached via the reference run (see the class Javadoc), never set directly.
        ContentStatus initialStatus = problem.status() == ContentStatus.PUBLISHED ? ContentStatus.DRAFT : problem.status();
        return new ProblemCommands.Create(problem.title(), problem.description(), problem.difficulty(),
                initialStatus, signature.methodName(), signature.returnType().type(), parameters,
                problem.testCases(), resolveTagIds(problem));
    }

    /**
     * The admin form lets a person confirm a guessed type (Python {@code int}, JavaScript
     * {@code number}); a seed run has nobody to ask, so a guess is an error — write the template in
     * Java, which spells every type exactly.
     */
    private static void requireUnambiguous(ParsedSignature signature, String title) {
        boolean ambiguous = !signature.returnType().alternatives().isEmpty()
                || signature.parameters().stream().anyMatch(p -> !p.type().alternatives().isEmpty());
        if (ambiguous) {
            throw new IllegalStateException("'" + title + "': the template's types are ambiguous (e.g. Python int, "
                    + "JavaScript number) — write the seed template in Java");
        }
    }

    /** Tags are referenced by name in seed files; every one must already exist (ProblemTagSeeder runs first). */
    private Set<Integer> resolveTagIds(SeedProblem problem) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String name : problem.tags()) {
            ProblemTag tag = problemTagRepository.findByNameIgnoreCase(name)
                    .orElseThrow(() -> new IllegalStateException(
                            "'" + problem.title() + "' references unknown tag '" + name + "'"));
            ids.add(tag.getId());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private SeedProblem read(Resource file) {
        String content;
        try {
            content = file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read problem seed file: " + file.getFilename(), e);
        }
        String[] parts = FRONT_MATTER_DELIMITER.split(content, 3);
        if (parts.length < 3) {
            throw new IllegalStateException(file.getFilename()
                    + " is missing its '---' front-matter delimiters (expected: ---, YAML, ---, Markdown description)");
        }

        Map<String, Object> meta = yaml.load(parts[1]);
        Map<String, Object> template = (Map<String, Object>) require(meta, "template", file);
        List<Map<String, Object>> testCases = (List<Map<String, Object>>) require(meta, "testCases", file);
        Map<String, Object> reference = (Map<String, Object>) meta.get("referenceSolution");
        List<Map<String, Object>> solutions = (List<Map<String, Object>>) meta.getOrDefault("solutions", List.of());

        return new SeedProblem(
                (String) require(meta, "title", file),
                parts[2].strip(),
                Difficulty.valueOf((String) require(meta, "difficulty", file)),
                ContentStatus.valueOf((String) meta.getOrDefault("status", "DRAFT")),
                (List<String>) meta.getOrDefault("tags", List.of()),
                ProgrammingLanguage.valueOf((String) require(template, "language", file)),
                (String) require(template, "code", file),
                testCases.stream()
                        .map(tc -> new ProblemCommands.TestCaseInput(
                                String.valueOf(require(tc, "input", file)),
                                String.valueOf(require(tc, "expectedOutput", file)),
                                Boolean.TRUE.equals(tc.get("sample"))))
                        .toList(),
                reference == null ? null : toReferenceSolution(reference, file),
                solutions.stream().map(s -> toSeedSolution(s, reference != null, file)).toList());
    }

    private static ReferenceSolution toReferenceSolution(Map<String, Object> map, Resource file) {
        return new ReferenceSolution(
                ProgrammingLanguage.valueOf((String) require(map, "language", file)),
                (String) require(map, "code", file));
    }

    @SuppressWarnings("unchecked")
    private static SeedSolution toSeedSolution(Map<String, Object> map, boolean hasReference, Resource file) {
        String title = (String) require(map, "title", file);
        boolean useReference = Boolean.TRUE.equals(map.get("useReferenceSolution"));
        if (useReference && !hasReference) {
            throw new IllegalStateException(file.getFilename() + ": solution '" + title
                    + "' sets useReferenceSolution but the file has no referenceSolution");
        }
        List<ReferenceSolution> implementations = ((List<Map<String, Object>>) map.getOrDefault("implementations", List.of()))
                .stream().map(i -> toReferenceSolution(i, file)).toList();
        return new SeedSolution(title, (String) require(map, "explanation", file), useReference, implementations);
    }

    private static Object require(Map<String, Object> map, String key, Resource file) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalStateException(file.getFilename() + " is missing the required field '" + key + "'");
        }
        return value;
    }

    /** One seed file, read but not yet validated. */
    private record SeedProblem(
            String title,
            String description,
            Difficulty difficulty,
            ContentStatus status,
            List<String> tags,
            ProgrammingLanguage templateLanguage,
            String templateCode,
            List<ProblemCommands.TestCaseInput> testCases,
            ReferenceSolution referenceSolution,
            List<SeedSolution> solutions) {
    }

    /** One editorial approach from a seed file's {@code solutions:} list. */
    record SeedSolution(String title, String explanation, boolean useReferenceSolution,
                        List<ReferenceSolution> implementations) {
    }

    /** A complete, accepted-by-the-judge solution for a seed problem; {@code null} on the record when absent. */
    record ReferenceSolution(ProgrammingLanguage language, String code) {
    }
}
