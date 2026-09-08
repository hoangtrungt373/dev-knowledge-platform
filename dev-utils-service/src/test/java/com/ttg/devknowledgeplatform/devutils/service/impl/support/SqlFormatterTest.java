package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class SqlFormatterTest {

    @Test
    void putsMajorClausesOnTheirOwnLine() {
        String result = SqlFormatter.beautify("select id, name from users where active = 1");

        assertThat(result).isEqualTo("select id, name\nfrom users\nwhere active = 1");
    }

    @Test
    void indentsAndOrOneLevelDeeperThanWhere() {
        String result = SqlFormatter.beautify("select * from users where active = 1 and age > 18");

        assertThat(result).isEqualTo("select *\nfrom users\nwhere active = 1\n  and age > 18");
    }

    @Test
    void indentsAParenthesizedSubqueryOneLevelDeeper() {
        String result = SqlFormatter.beautify("select * from (select id from users where active = 1) t");

        // "from(" with no space — `(` never gets a leading space, a deliberate trade-off (see
        // SqlFormatter's own Javadoc): wrong for a subquery like this one, but always correct for
        // a function call (`count(*)`, not `count (*)`), which this formatter treats as the more
        // important case to never get wrong.
        assertThat(result).isEqualTo(
                "select *\nfrom(\n  select id\n  from users\n  where active = 1) t"
        );
    }

    @Test
    void recognizesMultiWordJoinAndGroupByPhrases() {
        String result = SqlFormatter.beautify(
                "select * from a left outer join b on a.id = b.id group by a.id order by a.id");

        assertThat(result).isEqualTo(
                "select *\nfrom a\nleft outer join b\non a.id = b.id\ngroup by a.id\norder by a.id"
        );
    }

    @Test
    void functionCallsGetNoSpaceBeforeTheOpeningParen() {
        String result = SqlFormatter.beautify("select count(*) from users");

        assertThat(result).contains("count(*)");
    }

    @Test
    void keepsStringLiteralsVerbatimIncludingKeywordLikeContent() {
        String result = SqlFormatter.beautify("select * from users where name = 'select from where'");

        assertThat(result).contains("'select from where'");
        // Only one real WHERE clause should have been recognized — the keyword-like text inside
        // the string literal must not have triggered extra line breaks of its own.
        assertThat(result.lines().filter(line -> line.strip().startsWith("where")).count()).isEqualTo(1);
    }

    @Test
    void doesNotSplitCommaSeparatedColumnsOntoSeparateLines() {
        String result = SqlFormatter.beautify("select id, name, email from users");

        assertThat(result).contains("select id, name, email");
    }

    @Test
    void neverThrowsOnUnterminatedStringsOrComments() {
        assertThatCode(() -> SqlFormatter.beautify("select * from users where name = 'unterminated"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SqlFormatter.beautify("select * from users -- unterminated"))
                .doesNotThrowAnyException();
    }

    @Test
    void minifyStripsCommentsAndCollapsesOntoOneLine() {
        String result = SqlFormatter.minify("select id, name\n-- a comment\nfrom users\nwhere active = 1");

        assertThat(result).doesNotContain("comment").doesNotContain("\n")
                .isEqualTo("select id, name from users where active = 1");
    }

    @Test
    void minifyPreservesStringLiteralsVerbatim() {
        String result = SqlFormatter.minify("select * from users where name = 'a  b'");

        assertThat(result).contains("'a  b'");
    }
}
