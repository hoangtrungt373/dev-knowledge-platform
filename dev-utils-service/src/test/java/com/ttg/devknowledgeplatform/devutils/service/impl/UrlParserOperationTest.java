package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class UrlParserOperationTest {

    private static final String EXAMPLE_URL =
            "https://vuicoding.me:443/tools/dev-utils?tab=json&from=homepage#workspace";

    private ObjectMapper objectMapper;
    private UrlParserOperation operation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        operation = new UrlParserOperation(objectMapper);
    }

    @Test
    void parsesTheExactReportedExample() {
        String result = operation.execute(EXAMPLE_URL, false);

        // `port` is "" here, not the reported example's literal (non-English) placeholder text —
        // 443 is https's own default port, so it's normalized away exactly like every browser's
        // own `URL.port` already does; see this class's own note on `resolvePort`.
        assertThat(result).isEqualTo(
                "{\n"
                        + "  \"protocol\": \"https:\",\n"
                        + "  \"username\": \"\",\n"
                        + "  \"password\": \"\",\n"
                        + "  \"hostname\": \"vuicoding.me\",\n"
                        + "  \"port\": \"\",\n"
                        + "  \"pathname\": \"/tools/dev-utils\",\n"
                        + "  \"search\": \"?tab=json&from=homepage\",\n"
                        + "  \"query\": {\n"
                        + "    \"tab\": \"json\",\n"
                        + "    \"from\": \"homepage\"\n"
                        + "  },\n"
                        + "  \"hash\": \"#workspace\",\n"
                        + "  \"origin\": \"https://vuicoding.me\"\n"
                        + "}"
        );
    }

    @Test
    void producesCompactSingleLineOutputWhenMinified() throws Exception {
        String result = operation.execute(EXAMPLE_URL, true);

        assertThat(result).doesNotContain("\n");
        assertThat(objectMapper.readTree(result).get("hostname").asText()).isEqualTo("vuicoding.me");
        assertThat(objectMapper.readTree(result).get("query").get("tab").asText()).isEqualTo("json");
    }

    @Test
    void keepsANonDefaultPortAndIncludesItInOrigin() throws Exception {
        String result = operation.execute("https://example.com:8443/x", true);

        assertThat(objectMapper.readTree(result).get("port").asText()).isEqualTo("8443");
        assertThat(objectMapper.readTree(result).get("origin").asText()).isEqualTo("https://example.com:8443");
    }

    @Test
    void normalizesTheHttpDefaultPortAway() throws Exception {
        String result = operation.execute("http://example.com:80/x", true);

        assertThat(objectMapper.readTree(result).get("port").asText()).isEqualTo("");
        assertThat(objectMapper.readTree(result).get("origin").asText()).isEqualTo("http://example.com");
    }

    @Test
    void parsesUserinfoIntoUsernameAndPassword() throws Exception {
        String result = operation.execute("https://alice:secret@example.com/x", true);

        assertThat(objectMapper.readTree(result).get("username").asText()).isEqualTo("alice");
        assertThat(objectMapper.readTree(result).get("password").asText()).isEqualTo("secret");
    }

    @Test
    void lowercasesSchemeAndHostname() throws Exception {
        String result = operation.execute("HTTPS://Example.COM/Path", true);

        assertThat(objectMapper.readTree(result).get("protocol").asText()).isEqualTo("https:");
        assertThat(objectMapper.readTree(result).get("hostname").asText()).isEqualTo("example.com");
        // Path casing itself is left untouched — only scheme/host are normalized.
        assertThat(objectMapper.readTree(result).get("pathname").asText()).isEqualTo("/Path");
    }

    @Test
    void defaultsPathnameToASlashWhenAbsent() throws Exception {
        String result = operation.execute("https://example.com", true);

        assertThat(objectMapper.readTree(result).get("pathname").asText()).isEqualTo("/");
    }

    @Test
    void originIsTheOpaqueLiteralNullForANonNetworkScheme() throws Exception {
        String result = operation.execute("custom://myhost/path", true);

        assertThat(objectMapper.readTree(result).get("origin").asText()).isEqualTo("null");
        assertThat(objectMapper.readTree(result).get("hostname").asText()).isEqualTo("myhost");
    }

    @Test
    void aQueryKeyWithNoEqualsSignGetsAnEmptyStringValue() throws Exception {
        String result = operation.execute("https://example.com/?flag&tab=json", true);

        assertThat(objectMapper.readTree(result).get("query").get("flag").asText()).isEqualTo("");
        assertThat(objectMapper.readTree(result).get("query").get("tab").asText()).isEqualTo("json");
    }

    @Test
    void aDuplicateQueryKeyKeepsItsFirstPositionButTheLastValue() throws Exception {
        String result = operation.execute("https://example.com/?a=1&b=2&a=3", true);

        assertThat(objectMapper.readTree(result).get("query").toString())
                .isEqualTo("{\"a\":\"3\",\"b\":\"2\"}");
    }

    @Test
    void rejectsAMalformedUri() {
        assertThatThrownBy(() -> operation.execute("http://example.com/not a valid path", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_URL));
    }

    @Test
    void rejectsAUriWithNoSchemeOrHost() {
        assertThatThrownBy(() -> operation.execute("/just/a/path", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_URL));
    }

    @Test
    void rejectsAUriWithNoHostEvenIfSyntacticallyValid() {
        // "mailto:" is a syntactically valid URI (scheme + opaque part) but has no authority/host
        // component at all — genuinely different from "/just/a/path" (no scheme either), but
        // rejected for the same reason: this operation requires an absolute URL with a real host.
        assertThatThrownBy(() -> operation.execute("mailto:test@example.com", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DevUtilsErrorCode.INVALID_URL));
    }
}
