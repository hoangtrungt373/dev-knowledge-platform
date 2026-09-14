package com.ttg.devknowledgeplatform.devutils;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ttg.devknowledgeplatform.devutils.dto.DevUtilsLimits;

/**
 * Boots the real Spring context and hits every endpoint through the real filter chain via
 * {@link MockMvc} — verifies, end to end rather than by static reasoning alone, that this app
 * actually starts (the {@code GlobalExceptionHandler}/{@code spring-boot-starter-security}
 * classpath dependency reasoning in {@code security.SecurityConfig}'s own Javadoc holds up) and
 * that every one of the 40 operation endpoints is genuinely reachable with no
 * {@code Authorization} header at all.
 *
 * <p>{@link #assertOk}/{@link #assertBadRequest} exist because nearly every one of the ~50 test
 * methods below hand-rolled one of exactly two shapes — a real, found-by-audit duplication: ~30
 * "reachable" tests all built the identical {@code perform(post(...)).andExpect(status().isOk())
 * .andExpect(content().string(containsString(...)))} chain, and all 17 "malformed X returns 400"
 * tests all built the identical {@code .andExpect(status().isBadRequest()).andExpect(content()
 * .string(containsString("DEVUTILS_0NN")))} chain, differing only in the path/body/expected
 * fragment(s). Both helpers also share one {@code post(BASE_PATH + path)} construction, so a
 * future change to this module's own base path only needs updating in one place. Hamcrest's own
 * {@code containsString}/{@code not} are statically imported now too (previously fully qualified
 * on every one of ~56 uses) for the same "stop repeating what a single import already covers"
 * reason.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DevUtilsServiceApplicationTests {

    private static final String BASE_PATH = "/api/v1/dev-utils";

    @Autowired
    private MockMvc mockMvc;

    /**
     * Posts {@code body} to {@code BASE_PATH + path} and asserts a {@code 200} response whose body
     * contains every one of {@code expectedFragments} — the shape every "reachable with no
     * Authorization header" test below shares. Returns the underlying {@link ResultActions} for
     * the handful of call sites that need one extra assertion beyond a plain fragment match (e.g.
     * asserting a fragment is <em>absent</em> via {@link org.hamcrest.Matchers#not}).
     */
    private ResultActions assertOk(String path, String body, String... expectedFragments) throws Exception {
        ResultActions result = mockMvc.perform(post(BASE_PATH + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        for (String fragment : expectedFragments) {
            result.andExpect(content().string(containsString(fragment)));
        }
        return result;
    }

    /**
     * Posts {@code body} to {@code BASE_PATH + path} and asserts a {@code 400} response whose body
     * contains {@code errorCode} — the shape every "malformed X returns 400 through the shared
     * GlobalExceptionHandler" test below shares.
     */
    private void assertBadRequest(String path, String body, String errorCode) throws Exception {
        mockMvc.perform(post(BASE_PATH + path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(errorCode)));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void formatJsonIsReachableWithNoAuthentication() throws Exception {
        assertOk("/json/format", "{\"input\":\"{}\",\"minify\":false}", "output");
    }

    @Test
    void yamlToJsonIsReachableWithNoAuthentication() throws Exception {
        assertOk("/yaml-to-json", "{\"input\":\"name: Alice\",\"minify\":true}", "Alice");
    }

    @Test
    void jsonToYamlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/json-to-yaml", "{\"input\":\"{\\\"name\\\":\\\"Alice\\\"}\"}", "Alice");
    }

    @Test
    void beautifyHtmlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/html/beautify", "{\"input\":\"<p>Hi</p>\",\"minify\":false}", "Hi");
    }

    @Test
    void beautifyCssIsReachableWithNoAuthentication() throws Exception {
        assertOk("/css/beautify", "{\"input\":\".a{color:red;}\",\"minify\":false}", "color: red");
    }

    @Test
    void beautifyLessIsReachableWithNoAuthentication() throws Exception {
        assertOk("/less/beautify", "{\"input\":\".a{@width: 10px;}\",\"minify\":true}", "@width");
    }

    @Test
    void beautifyScssIsReachableWithNoAuthentication() throws Exception {
        assertOk("/scss/beautify", "{\"input\":\".a{$width: 10px;}\",\"minify\":true}", "$width");
    }

    @Test
    void beautifyJsIsReachableWithNoAuthentication() throws Exception {
        assertOk("/js/beautify", "{\"input\":\"function f(){return 1;}\",\"minify\":false}", "return 1");
    }

    @Test
    void beautifyErbIsReachableWithNoAuthentication() throws Exception {
        assertOk("/erb/beautify", "{\"input\":\"<p><%= name %></p>\",\"minify\":false}", "<%= name %>");
    }

    @Test
    void beautifyXmlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/xml/beautify", "{\"input\":\"<root><child>Hi</child></root>\",\"minify\":false}", "Hi");
    }

    @Test
    void malformedXmlReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/xml/beautify", "{\"input\":\"<root><unclosed></root>\",\"minify\":false}", "DEVUTILS_003");
    }

    @Test
    void jsonToCsvIsReachableWithNoAuthentication() throws Exception {
        assertOk("/json-to-csv", "{\"input\":\"[{\\\"id\\\":1,\\\"name\\\":\\\"Alice\\\"}]\"}", "Alice");
    }

    @Test
    void csvToJsonIsReachableWithNoAuthentication() throws Exception {
        assertOk("/csv-to-json", "{\"input\":\"id,name\\n1,Alice\\n\",\"minify\":true}", "Alice");
    }

    @Test
    void malformedCsvReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/csv-to-json", "{\"input\":\"id,name\\n1,Alice,extra\\n\",\"minify\":true}", "DEVUTILS_004");
    }

    @Test
    void formatSqlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/sql/format", "{\"input\":\"select id from users\",\"minify\":false}", "SELECT");
    }

    @Test
    void phpToJsonIsReachableWithNoAuthentication() throws Exception {
        assertOk("/php-to-json", "{\"input\":\"['name' => 'DevKnowledge']\",\"minify\":true}", "DevKnowledge");
    }

    @Test
    void malformedPhpReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/php-to-json", "{\"input\":\"['a', 'b'\",\"minify\":false}", "DEVUTILS_005");
    }

    @Test
    void jsonToPhpIsReachableWithNoAuthentication() throws Exception {
        assertOk("/json-to-php", "{\"input\":\"{\\\"name\\\":\\\"DevKnowledge\\\"}\",\"minify\":true}", "DevKnowledge");
    }

    @Test
    void convertStringCaseIsReachableWithNoAuthentication() throws Exception {
        assertOk("/string-case/convert", "{\"input\":\"DevKnowledge\"}", "devKnowledge");
    }

    @Test
    void encodeBase64IsReachableWithNoAuthentication() throws Exception {
        assertOk("/base64/encode", "{\"input\":\"DevKnowledge\"}", "RGV2S25vd2xlZGdl");
    }

    @Test
    void decodeBase64IsReachableWithNoAuthentication() throws Exception {
        assertOk("/base64/decode", "{\"input\":\"RGV2S25vd2xlZGdl\"}", "DevKnowledge");
    }

    @Test
    void malformedBase64Returns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/base64/decode", "{\"input\":\"not valid base64!!\"}", "DEVUTILS_006");
    }

    @Test
    void encodeUrlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/url/encode", "{\"input\":\"DevKnowledge & Co\"}", "DevKnowledge%20%26%20Co");
    }

    @Test
    void decodeUrlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/url/decode", "{\"input\":\"DevKnowledge%20%26%20Co\"}", "DevKnowledge & Co");
    }

    @Test
    void malformedUrlEncodingReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/url/decode", "{\"input\":\"100%\"}", "DEVUTILS_007");
    }

    @Test
    void encodeHtmlEntityIsReachableWithNoAuthentication() throws Exception {
        assertOk("/html-entity/encode", "{\"input\":\"<b>Dev</b> & \\\"Knowledge\\\"\"}",
                "&lt;b&gt;Dev&lt;/b&gt; &amp; &quot;Knowledge&quot;");
    }

    @Test
    void decodeHtmlEntityIsReachableWithNoAuthentication() throws Exception {
        assertOk("/html-entity/decode", "{\"input\":\"&lt;b&gt;Dev&lt;/b&gt; &amp; &quot;Knowledge&quot;\"}",
                "<b>Dev</b> & \\\"Knowledge\\\"");
    }

    @Test
    void generateHashIsReachableWithNoAuthentication() throws Exception {
        assertOk("/hash/generate", "{\"input\":\"DevKnowledge\"}",
                "35abd90ddf25c6b7ee67d7f5dfbbbc48332b5e87",
                "f8bdef26435989071098c46c93337f649309bb63b00bd773a2abcbecfbfbc68a");
    }

    @Test
    void serializePhpIsReachableWithNoAuthentication() throws Exception {
        assertOk("/php-serialize/serialize",
                "{\"input\":\"{\\\"name\\\":\\\"DevKnowledge\\\",\\\"active\\\":true,\\\"count\\\":47}\"}",
                "a:3:{s:4:\\\"name\\\";s:12:\\\"DevKnowledge\\\";s:6:\\\"active\\\";b:1;s:5:\\\"count\\\";i:47;}");
    }

    @Test
    void unserializePhpIsReachableWithNoAuthentication() throws Exception {
        assertOk("/php-serialize/unserialize", "{\"input\":\"a:1:{s:4:\\\"name\\\";s:12:\\\"DevKnowledge\\\";}\"}",
                "DevKnowledge");
    }

    @Test
    void malformedSerializedPhpReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/php-serialize/unserialize", "{\"input\":\"a:1:{\"}", "DEVUTILS_008");
    }

    @Test
    void encodeHexIsReachableWithNoAuthentication() throws Exception {
        assertOk("/hex/encode", "{\"input\":\"Hi\"}", "48 69");
    }

    @Test
    void decodeHexIsReachableWithNoAuthentication() throws Exception {
        assertOk("/hex/decode", "{\"input\":\"48 69\"}", "Hi");
    }

    @Test
    void malformedHexReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/hex/decode", "{\"input\":\"zz\"}", "DEVUTILS_009");
    }

    @Test
    void debugJwtIsReachableWithNoAuthentication() throws Exception {
        assertOk("/jwt/debug",
                "{\"input\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                        + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkRldktub3dsZWRnZSIsImlhdCI6MTUxNjIzOTAyMn0"
                        + ".demo-signature\",\"minify\":false}",
                "HS256", "DevKnowledge", "demo-signature");
    }

    @Test
    void malformedJwtReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/jwt/debug", "{\"input\":\"only.two\",\"minify\":false}", "DEVUTILS_010");
    }

    @Test
    void testRegexpIsReachableWithNoAuthentication() throws Exception {
        assertOk("/regexp/test",
                "{\"pattern\":\"[\\\\w.+-]+@[\\\\w.-]+\\\\.[a-zA-Z]{2,}\",\"flags\":\"gi\","
                        + "\"testText\":\"hello@example.com\\nsupport@example.org\\nnot-an-email\"}",
                "hello@example.com", "support@example.org");
    }

    @Test
    void malformedRegexReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/regexp/test", "{\"pattern\":\"[unclosed\",\"flags\":\"g\",\"testText\":\"anything\"}", "DEVUTILS_011");
    }

    @Test
    void parseUrlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/url/parse",
                "{\"input\":\"https://devknowledge.io:443/tools/dev-utils?tab=json&from=homepage"
                        + "#workspace\",\"minify\":false}",
                "devknowledge.io", "tab=json");
    }

    @Test
    void malformedUrlReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/url/parse", "{\"input\":\"/just/a/path\",\"minify\":false}", "DEVUTILS_013");
    }

    @Test
    void parseCronIsReachableWithNoAuthentication() throws Exception {
        assertOk("/cron/parse", "{\"input\":\"0 9 * * 1-5\"}", "Monday through Friday");
    }

    @Test
    void malformedCronReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/cron/parse", "{\"input\":\"0 9 * *\"}", "DEVUTILS_014");
    }

    @Test
    void compareTextDiffIsReachableWithNoAuthentication() throws Exception {
        assertOk("/text-diff/compare", "{\"original\":\"a\\nb\",\"updated\":\"a\\nc\"}",
                "\"addedCount\":1", "\"removedCount\":1");
    }

    @Test
    void tooManyDiffLinesReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        String tooManyLines = "x\\n".repeat(2001);

        assertBadRequest("/text-diff/compare", "{\"original\":\"" + tooManyLines + "\",\"updated\":\"a\"}", "DEVUTILS_015");
    }

    @Test
    void convertDateTimeToTimestampIsReachableWithNoAuthentication() throws Exception {
        assertOk("/datetime-to-timestamp", "{\"dateTime\":\"1970-01-01T00:00:00\",\"zoneId\":\"UTC\"}",
                "\"epochSeconds\":0");
    }

    @Test
    void malformedDateTimeReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/datetime-to-timestamp", "{\"dateTime\":\"not-a-date\",\"zoneId\":\"\"}", "DEVUTILS_016");
    }

    @Test
    void convertTimestampToDateTimeIsReachableWithNoAuthentication() throws Exception {
        assertOk("/timestamp-to-datetime", "{\"timestamp\":\"0\",\"zoneId\":\"UTC\"}", "1970-01-01");
    }

    @Test
    void malformedTimestampReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/timestamp-to-datetime", "{\"timestamp\":\"not-a-number\",\"zoneId\":\"\"}", "DEVUTILS_017");
    }

    @Test
    void unrecognizedTimeZoneReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/timestamp-to-datetime", "{\"timestamp\":\"0\",\"zoneId\":\"Not/AZone\"}", "DEVUTILS_018");
    }

    @Test
    void previewHtmlIsReachableWithNoAuthentication() throws Exception {
        assertOk("/html/preview", "{\"input\":\"<p>Hi</p><script>alert(1)</script>\"}", "Hi")
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    void previewMarkdownIsReachableWithNoAuthentication() throws Exception {
        assertOk("/markdown/preview", "{\"input\":\"# Hi\\n\\n<script>alert(1)</script>\"}", "<h1>Hi</h1>")
                .andExpect(content().string(not(containsString("script"))));
    }

    @Test
    void convertHtmlToTsxIsReachableWithNoAuthentication() throws Exception {
        assertOk("/html/to-tsx", "{\"input\":\"<div class=\\\"card\\\">Hi</div>\",\"minify\":true}",
                "className=\\\"card\\\"");
    }

    @Test
    void convertColorIsReachableWithNoAuthentication() throws Exception {
        assertOk("/color/convert", "{\"input\":\"#14B8A6\"}", "rgb(20, 184, 166)");
    }

    @Test
    void malformedColorReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/color/convert", "{\"input\":\"not-a-color\"}", "DEVUTILS_019");
    }

    @Test
    void convertSvgToCssIsReachableWithNoAuthentication() throws Exception {
        assertOk("/svg/to-css", "{\"input\":\"<svg><rect/></svg>\",\"minify\":true}", ".icon{background-image:url");
    }

    @Test
    void generateLoremIpsumIsReachableWithNoAuthentication() throws Exception {
        assertOk("/lorem-ipsum/generate", "{\"paragraphs\":3}", "Lorem ipsum dolor sit amet");
    }

    @Test
    void paragraphCountOutOfRangeFailsBeanValidationWith400() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/lorem-ipsum/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paragraphs\":21}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        assertBadRequest("/json/format", "{\"input\":\"not valid json\",\"minify\":false}", "DEVUTILS_001");
    }

    @Test
    void blankInputFailsBeanValidationWith400() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"\",\"minify\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inputOverTheSizeCapFailsBeanValidationWith400() throws Exception {
        String oversizedInput = "1".repeat(DevUtilsLimits.MAX_INPUT_LENGTH + 1);

        mockMvc.perform(post(BASE_PATH + "/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"" + oversizedInput + "\",\"minify\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inputAtExactlyTheSizeCapIsAccepted() throws Exception {
        // A JSON array holding one long string ["aaa...a"] is valid JSON of an exact, easy-to-
        // compute length, and — unlike a run of bare digits — doesn't trip Jackson's own separate
        // StreamReadConstraints.getMaxNumberLength() guard (a single JSON number token capped at
        // 1000 digits by default), which is a different, pre-existing Jackson safety limit, not
        // the @Size cap this test is actually about.
        String innerJson = "[\"" + "a".repeat(DevUtilsLimits.MAX_INPUT_LENGTH - 4) + "\"]";
        String escapedForRequestBody = innerJson.replace("\"", "\\\"");

        mockMvc.perform(post(BASE_PATH + "/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"" + escapedForRequestBody + "\",\"minify\":true}"))
                .andExpect(status().isOk());
    }
}
