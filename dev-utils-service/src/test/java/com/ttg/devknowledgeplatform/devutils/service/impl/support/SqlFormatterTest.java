package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class SqlFormatterTest {

    @Test
    void putsEachClauseOnItsOwnLineWithItsBodyIndentedUnderIt() {
        String result = SqlFormatter.beautify("select id, name from users where active = 1");

        assertThat(result).isEqualTo("SELECT\n  id,\n  name\nFROM\n  users\nWHERE\n  active = 1");
    }

    @Test
    void reproducesTheExactReportedExample() {
        // The exact reported bug — uppercase keywords/literals, one column/item per line, and
        // JOIN ... ON kept together on one line rather than ON getting its own.
        String result = SqlFormatter.beautify(
                "select u.id,u.name,count(p.id) as posts from users u left join posts p "
                        + "on p.user_id=u.id where u.active=true group by u.id,u.name order by posts desc;"
        );

        assertThat(result).isEqualTo(
                "SELECT\n"
                        + "  u.id,\n"
                        + "  u.name,\n"
                        + "  count(p.id) AS posts\n"
                        + "FROM\n"
                        + "  users u\n"
                        + "  LEFT JOIN posts p ON p.user_id = u.id\n"
                        + "WHERE\n"
                        + "  u.active = TRUE\n"
                        + "GROUP BY\n"
                        + "  u.id,\n"
                        + "  u.name\n"
                        + "ORDER BY\n"
                        + "  posts DESC;"
        );
    }

    @Test
    void uppercasesRecognizedKeywordsAndLiteralsButNeverAFunctionNameOrIdentifier() {
        // "count" is deliberately NOT a recognized keyword (it's a function name/identifier, not
        // SQL grammar) — see SqlFormatter's own Javadoc for why COUNT/SUM/AVG/etc. are excluded
        // from all three keyword lists on purpose.
        String result = SqlFormatter.beautify("select count(*) as total from users where active = true");

        assertThat(result).contains("count(*)").doesNotContain("COUNT(*)").contains("AS total").contains("TRUE");
    }

    @Test
    void andOrShareTheSameIndentLevelAsTheRestOfTheClauseBody() {
        // A previous version of this class gave AND/OR one extra indent level beyond the clause's
        // own body — reversed once the clause's first condition itself moved onto its own indented
        // body line (rather than staying inline with the clause keyword): AND/OR now align with
        // that same body level, the same treatment JOIN already gets within FROM's own body.
        String result = SqlFormatter.beautify("select * from users where active = 1 and age > 18");

        assertThat(result).isEqualTo("SELECT\n  *\nFROM\n  users\nWHERE\n  active = 1\n  AND age > 18");
    }

    @Test
    void indentsAParenthesizedSubqueryOneLevelDeeper() {
        String result = SqlFormatter.beautify("select * from (select id from users where active = 1) t");

        // Known, documented imprecision (see SqlFormatter's own Javadoc): the opening "(" — the
        // first token of FROM's own body — starts its own body line per the usual rule, but the
        // nested SELECT immediately after it also forces its own fresh line, leaving the "(" alone
        // on a line with nothing else on it. Doesn't corrupt the SQL text, just an unusual-looking
        // layout for a subquery specifically; not chased further, the same "lenient heuristic, not
        // a full parser" trade-off this whole class already accepts elsewhere.
        assertThat(result).isEqualTo(
                "SELECT\n"
                        + "  *\n"
                        + "FROM\n"
                        + "  (\n"
                        + "  SELECT\n"
                        + "    id\n"
                        + "  FROM\n"
                        + "    users\n"
                        + "  WHERE\n"
                        + "    active = 1) t"
        );
    }

    @Test
    void recognizesMultiWordJoinAndGroupByPhrasesKeepingJoinOnTogether() {
        String result = SqlFormatter.beautify(
                "select * from a left outer join b on a.id = b.id group by a.id order by a.id");

        assertThat(result).isEqualTo(
                "SELECT\n"
                        + "  *\n"
                        + "FROM\n"
                        + "  a\n"
                        + "  LEFT OUTER JOIN b ON a.id = b.id\n"
                        + "GROUP BY\n"
                        + "  a.id\n"
                        + "ORDER BY\n"
                        + "  a.id"
        );
    }

    @Test
    void preferstheLongestMatchingKeywordPhraseRegardlessOfListOrder() {
        // matchKeywordPhrase() always prefers the longest match (LEFT OUTER JOIN over LEFT JOIN,
        // UNION ALL over UNION) — this is now an explicit guarantee, not one that depends on each
        // keyword list's own declaration order (see this class's own Javadoc). If the shorter
        // "UNION" phrase had won instead, "ALL" would render as a separate, unrecognized ordinary
        // token straight after it on the same line, rather than "UNION ALL" appearing together as
        // its own clause line.
        String result = SqlFormatter.beautify("select * from a union all select * from b");

        assertThat(result).isEqualTo(
                "SELECT\n  *\nFROM\n  a\nUNION ALL\nSELECT\n  *\nFROM\n  b"
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
        // the string literal (still lowercase, since string contents are never touched) must not
        // have triggered extra line breaks or been mistaken for the real, now-uppercased keyword.
        assertThat(result.lines().filter(line -> line.strip().equals("WHERE")).count()).isEqualTo(1);
    }

    @Test
    void splitsACommaSeparatedListOntoOneItemPerLineScopedToTheClausesOwnParenDepth() {
        // Reverses a previously deliberate "never splits a comma-separated list" design choice,
        // per direct request (confirmed before implementing — see SqlFormatter's own Javadoc).
        // Scoped to the clause's own base paren depth: the comma inside count(id, other) — a
        // function call's own argument list — must NOT also trigger a split, only the two
        // *outer* commas (between id, name, and the count(...) expression) do.
        String result = SqlFormatter.beautify("select id, name, count(id, other) as n from users");

        assertThat(result).isEqualTo(
                "SELECT\n"
                        + "  id,\n"
                        + "  name,\n"
                        + "  count(id, other) AS n\n"
                        + "FROM\n"
                        + "  users"
        );
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
                .isEqualTo("SELECT id, name FROM users WHERE active = 1");
    }

    @Test
    void minifyUppercasesKeywordsTooButNeverSplitsOntoMultipleLines() {
        String result = SqlFormatter.minify("select id, name, email from users");

        assertThat(result).isEqualTo("SELECT id, name, email FROM users");
    }

    @Test
    void minifyPreservesStringLiteralsVerbatim() {
        String result = SqlFormatter.minify("select * from users where name = 'a  b'");

        assertThat(result).contains("'a  b'");
    }
}
