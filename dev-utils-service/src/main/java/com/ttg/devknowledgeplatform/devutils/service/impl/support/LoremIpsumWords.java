package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.List;

/**
 * The fixed pool of genuine Latin filler words {@code LoremIpsumGeneratorOperation} assembles
 * random sentences from — drawn from the traditional "Lorem Ipsum" passage (adapted from Cicero's
 * "de Finibus Bonorum et Malorum", 45 BC), the same source text every real Lorem Ipsum generator
 * (lipsum.com included) draws its own vocabulary from, so the generated output reads as
 * recognizable placeholder Latin rather than a random string of syllables.
 */
public final class LoremIpsumWords {

    public static final List<String> WORDS = List.of(
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed",
            "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna",
            "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco",
            "laboris", "nisi", "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute",
            "irure", "in", "reprehenderit", "voluptate", "velit", "esse", "cillum", "eu", "fugiat",
            "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident",
            "sunt", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum",
            "at", "vero", "eos", "accusamus", "iusto", "odio", "dignissimos", "ducimus",
            "blanditiis", "praesentium", "voluptatum", "deleniti", "atque", "corrupti", "quos",
            "quas", "molestias", "excepturi", "occaecati", "cupiditate", "provident", "similique",
            "cum", "necessitatibus", "saepe", "eveniet", "fuga", "voluptates", "repudiandae",
            "recusandae", "itaque", "earum", "hic", "tenetur", "sapiente", "delectus", "reiciendis",
            "maiores", "alias", "perferendis", "doloribus", "asperiores", "repellat", "facilis",
            "libero", "tempore", "cum", "soluta", "nobis", "eligendi", "optio", "cumque", "nihil",
            "impedit", "quo", "minus", "quod", "maxime", "placeat", "facere", "possimus", "omnis",
            "assumenda", "repellendus", "temporibus", "autem", "quibusdam", "officiis", "debitis",
            "rerum", "necessitatibus", "saepe", "eveniet", "voluptas", "assumenda", "corporis",
            "suscipit", "laboriosam", "nisi", "aliquid", "ex", "ea", "commodi", "consequatur",
            "quis", "autem", "vel", "eum", "iure", "reprehenderit", "qui", "feugiat", "nulla",
            "facilisis", "vestibulum", "ante", "ipsum", "primis", "faucibus", "orci", "luctus");

    private LoremIpsumWords() {
    }
}
