package com.ttg.devknowledgeplatform.infra.service.seed;

/**
 * Documentation-only marker for every startup data seeder in the reactor ({@code CsvSeeder}
 * subclasses and the ones that implement their own {@code seed()} directly, e.g.
 * {@code content-service}'s {@code QuestionAnswerSeeder}, {@code social-service}'s
 * {@code FriendGraphSeeder}/{@code DmThreadSeeder}) — lets an IDE's "Find Implementations" list
 * every seeder across every module in one view, the same purpose {@code infra}'s
 * {@code ApplicationEventHandler} marker serves for event handlers.
 *
 * <p><strong>Deliberately not used for polymorphic invocation.</strong> {@code gateway}'s
 * {@code DataSeedingRunner} still injects and calls each seeder by name in an explicit, hardcoded
 * order (categories → tags → Q&amp;A → users → friend graph → DM threads → blocks), because that
 * order encodes real cross-entity dependencies (e.g. a DM thread can't be seeded before the friend
 * graph it depends on exists). Looping over an injected {@code List<Seeder>} instead would hand
 * that ordering over to Spring's bean-registration order, which is not guaranteed to match —
 * don't refactor {@code DataSeedingRunner} to do that.
 */
public interface Seeder {

    /**
     * Runs this seeder, inserting whatever rows aren't already present.
     *
     * @return the number of rows/entities inserted
     */
    int seed();
}
