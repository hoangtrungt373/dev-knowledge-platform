package com.ttg.devknowledgeplatform.content.service.seed;

import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.content.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.content.repository.QuestionAnswerRepository;
import com.ttg.devknowledgeplatform.content.repository.TagRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Seeds {@link QuestionAnswer} rows — each backed by a {@link ContentItem} — from one
 * Markdown-with-YAML-front-matter file per question under {@code data/question-answers/}.
 *
 * <p>Front matter carries the short structured fields ({@code id}, {@code title}, {@code slug},
 * {@code categoryId}, {@code tagIds}, {@code difficulty}, {@code isCommon},
 * {@code questionBody}, {@code shortAnswer}); everything after the closing {@code ---} line is
 * the raw markdown {@code detailedAnswer} body — the one field that actually benefits from
 * unescaped, syntax-highlighted prose editing. {@code difficulty}/{@code isCommon} are optional
 * — this content is general dev-knowledge Q&A, not only interview prep, and forcing an
 * interview-difficulty/frequency judgment call on plain "how does X work" content wouldn't mean
 * anything.
 *
 * <p>{@code categoryId}/{@code tagIds} reference {@link Category}/{@link Tag} by their permanent
 * {@code seedId} (the same {@code id} field {@link CategorySeeder}/{@link TagSeeder} assign),
 * not by name or slug — a category/tag's display {@code name} can be edited freely without
 * breaking every question that references it, which referencing by name would not survive.
 *
 * <p><b>{@code id} is required</b> — a permanent, seed-file-only identifier (persisted as
 * {@code ContentItem.seedId}, never shown to end users) and the sole idempotency key. It's
 * deliberately decoupled from {@code title}/{@code slug} so those stay freely editable across
 * re-runs without risking a duplicate insert: this seed data is long-lived and coexists
 * permanently with user-created content, not throwaway dev data. Reusing {@code slug} (derived
 * from {@code title}) as the idempotency key, as an earlier version of this seeder did, would
 * insert a duplicate question the moment its title was reworded.
 *
 * <p>{@code slug} stays a separate, independent, optional field — the production-facing URL
 * column, unrelated to idempotency. Omit it to derive from {@code title} via
 * {@link SlugService#toSlug(String)} (guaranteed-aligned with production content creation, but
 * verbose since titles are full sentences), or supply a short explicit one for readability.
 *
 * <p>Unlike {@link CategorySeeder}/{@link TagSeeder}, this does not extend {@link CsvSeeder}:
 * the source is a directory of files rather than rows in one file, so the iteration shape
 * genuinely differs and forcing a shared template would need a weaker, less readable
 * abstraction than just implementing {@code seed()} directly.
 *
 * <p>Requires the referenced category and tags to already exist — run {@link CategorySeeder}
 * and {@link TagSeeder} first.
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionAnswerSeeder implements Seeder {

    private static final String QUESTIONS_CLASSPATH_LOCATION = "classpath*:data/question-answers/*.md";
    private static final Pattern FRONT_MATTER_DELIMITER = Pattern.compile("(?m)^---\\s*$");
    // Body files lead with "<!-- detailedAnswer -->" purely so the field mapping is visible
    // when reading the raw file — front matter already labels questionBody/shortAnswer, so the
    // body needs an equivalent label instead of silently being "whatever comes after the ---".
    private static final Pattern DETAILED_ANSWER_LABEL = Pattern.compile("^<!--\\s*detailedAnswer\\s*-->\\s*");

    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final SlugService slugService;

    // SafeConstructor restricts parsing to plain YAML types (Map/List/String/Boolean/…) —
    // these files are developer-authored and trusted, but there's no reason to allow YAML's
    // arbitrary-Java-type-instantiation tags for a front-matter parser that only ever needs scalars and lists.
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    /**
     * Reads every {@code .md} file under {@code data/question-answers/} and inserts the
     * ones whose {@code id} is not already present.
     *
     * @return the number of rows inserted
     */
    @Override
    public int seed() {
        Resource[] files;
        try {
            files = new PathMatchingResourcePatternResolver().getResources(QUESTIONS_CLASSPATH_LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list question files: " + QUESTIONS_CLASSPATH_LOCATION, e);
        }

        int inserted = 0;
        int skipped = 0;
        for (Resource file : files) {
            ParsedQuestion question = parse(file);
            if (question.id() == null || question.id().isBlank()) {
                throw new IllegalStateException(file.getFilename() + " is missing the required front-matter field 'id'");
            }

            Optional<ContentItem> existingById = contentItemRepository.findBySeedId(question.id());
            if (existingById.isPresent()) {
                if (!existingById.get().getTitle().equals(question.title())) {
                    throw new IllegalStateException("id '" + question.id() + "' from " + file.getFilename()
                            + " is already used by a different question ('" + existingById.get().getTitle()
                            + "') — an id must never be reused for a different question");
                }
                skipped++;
                log.debug("QuestionAnswerSeeder: skipping existing row '{}'", question.id());
                continue;
            }

            String slug = resolveSlug(question);
            Optional<ContentItem> existingBySlug = contentItemRepository.findBySlug(slug);
            if (existingBySlug.isPresent()) {
                throw new IllegalStateException("Slug '" + slug + "' for id '" + question.id()
                        + "' already belongs to a different question ('" + existingBySlug.get().getTitle()
                        + "') — supply an explicit, distinct `slug:`");
            }

            persist(question, slug);
            inserted++;
        }

        log.info("QuestionAnswerSeeder: inserted {} row(s), skipped {} already-present row(s)", inserted, skipped);
        return inserted;
    }

    /** Explicit front-matter slug wins; otherwise derive from the title exactly as production content creation does. */
    private String resolveSlug(ParsedQuestion question) {
        return (question.slug() != null && !question.slug().isBlank())
                ? question.slug()
                : slugService.toSlug(question.title());
    }

    @SuppressWarnings("unchecked")
    private ParsedQuestion parse(Resource file) {
        String content;
        try {
            content = file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read question file: " + file.getFilename(), e);
        }

        String[] parts = FRONT_MATTER_DELIMITER.split(content, 3);
        if (parts.length < 3) {
            throw new IllegalStateException(file.getFilename()
                    + " is missing the '---' front-matter delimiters (expected: ---, YAML block, ---, markdown body)");
        }

        Map<String, Object> meta = yaml.load(parts[1]);
        List<String> tagIds = (List<String>) meta.getOrDefault("tagIds", List.of());
        String detailedAnswer = DETAILED_ANSWER_LABEL.matcher(parts[2].strip()).replaceFirst("").strip();

        return new ParsedQuestion(
                (String) meta.get("id"),
                (String) meta.get("title"),
                (String) meta.get("slug"),
                (String) meta.get("categoryId"),
                tagIds,
                (String) meta.get("difficulty"),
                (Boolean) meta.get("isCommon"),
                (String) meta.get("questionBody"),
                (String) meta.get("shortAnswer"),
                detailedAnswer
        );
    }

    private void persist(ParsedQuestion question, String slug) {
        Category category = categoryRepository.findBySeedId(question.categoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "Question '" + question.id() + "' references unknown categoryId '"
                                + question.categoryId() + "'"));

        ContentItem contentItem = new ContentItem();
        contentItem.setSeedId(question.id());
        contentItem.setType(ContentType.QUESTION_ANSWER);
        contentItem.setStatus(ContentStatus.PUBLISHED);
        contentItem.setTitle(question.title());
        contentItem.setSlug(slug);
        contentItem.setCategory(category);
        contentItem.setViewCount(0);
        contentItem.setPublishedAt(Instant.now());

        for (String tagId : question.tagIds()) {
            Tag tag = tagRepository.findBySeedId(tagId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Question '" + question.id() + "' references unknown tagId '" + tagId + "'"));
            ContentItemTag join = new ContentItemTag();
            join.setContentItem(contentItem);
            join.setTag(tag);
            contentItem.getContentItemTags().add(join);
        }

        QuestionAnswer questionAnswer = new QuestionAnswer();
        String difficulty = question.difficulty();
        questionAnswer.setDifficulty(difficulty != null && !difficulty.isBlank()
                ? QuestionDifficulty.valueOf(difficulty.trim().toUpperCase())
                : null);
        questionAnswer.setQuestionBody(question.questionBody());
        questionAnswer.setShortAnswer(question.shortAnswer());
        questionAnswer.setDetailedAnswer(question.detailedAnswer());
        questionAnswer.setIsCommon(question.isCommon());

        ContentItem savedContentItem = contentItemRepository.save(contentItem);
        questionAnswer.setContentItem(savedContentItem);
        questionAnswerRepository.save(questionAnswer);
    }

    /** Parsed front matter + body for one question file, prior to persistence. */
    private record ParsedQuestion(
            String id,
            String title,
            String slug,
            String categoryId,
            List<String> tagIds,
            String difficulty,
            Boolean isCommon,
            String questionBody,
            String shortAnswer,
            String detailedAnswer) {
    }
}
