package com.ttg.devknowledgeplatform.devutils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ttg.devknowledgeplatform.devutils.dto.DevUtilsLimits;

/**
 * Boots the real Spring context and hits every endpoint through the real filter chain via
 * {@link MockMvc} — verifies, end to end rather than by static reasoning alone, that this app
 * actually starts (the {@code GlobalExceptionHandler}/{@code spring-boot-starter-security}
 * classpath dependency reasoning in {@code security.SecurityConfig}'s own Javadoc holds up) and
 * that every one of the 29 operation endpoints is genuinely reachable with no
 * {@code Authorization} header at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DevUtilsServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void formatJsonIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{}\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("output")));
    }

    @Test
    void yamlToJsonIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/yaml-to-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"name: Alice\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
    }

    @Test
    void jsonToYamlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json-to-yaml")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{\\\"name\\\":\\\"Alice\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
    }

    @Test
    void beautifyHtmlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/html/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"<p>Hi</p>\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hi")));
    }

    @Test
    void beautifyCssIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/css/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\".a{color:red;}\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("color: red")));
    }

    @Test
    void beautifyLessIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/less/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\".a{@width: 10px;}\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@width")));
    }

    @Test
    void beautifyScssIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/scss/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\".a{$width: 10px;}\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("$width")));
    }

    @Test
    void beautifyJsIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/js/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"function f(){return 1;}\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("return 1")));
    }

    @Test
    void beautifyErbIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/erb/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"<p><%= name %></p>\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<%= name %>")));
    }

    @Test
    void beautifyXmlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/xml/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"<root><child>Hi</child></root>\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hi")));
    }

    @Test
    void malformedXmlReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/xml/beautify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"<root><unclosed></root>\",\"minify\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_003")));
    }

    @Test
    void jsonToCsvIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json-to-csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"[{\\\"id\\\":1,\\\"name\\\":\\\"Alice\\\"}]\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
    }

    @Test
    void csvToJsonIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/csv-to-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"id,name\\n1,Alice\\n\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
    }

    @Test
    void malformedCsvReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/csv-to-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"id,name\\n1,Alice,extra\\n\",\"minify\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_004")));
    }

    @Test
    void formatSqlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/sql/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"select id from users\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SELECT")));
    }

    @Test
    void phpToJsonIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/php-to-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"['name' => 'Vui Coding']\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui Coding")));
    }

    @Test
    void malformedPhpReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/php-to-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"['a', 'b'\",\"minify\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_005")));
    }

    @Test
    void jsonToPhpIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json-to-php")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{\\\"name\\\":\\\"Vui Coding\\\"}\",\"minify\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui Coding")));
    }

    @Test
    void convertStringCaseIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/string-case/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Vui Coding\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("vuiCoding")));
    }

    @Test
    void encodeBase64IsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/base64/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Vui Coding\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VnVpIENvZGluZw==")));
    }

    @Test
    void decodeBase64IsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/base64/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"VnVpIENvZGluZw==\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui Coding")));
    }

    @Test
    void malformedBase64Returns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/base64/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"not valid base64!!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_006")));
    }

    @Test
    void encodeUrlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/url/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Vui Coding & Co\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui%20Coding%20%26%20Co")));
    }

    @Test
    void decodeUrlIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/url/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Vui%20Coding%20%26%20Co\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui Coding & Co")));
    }

    @Test
    void malformedUrlEncodingReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/url/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"100%\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_007")));
    }

    @Test
    void encodeHtmlEntityIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/html-entity/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"<b>Vui</b> & \\\"Coding\\\"\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;b&gt;Vui&lt;/b&gt; &amp; &quot;Coding&quot;")));
    }

    @Test
    void decodeHtmlEntityIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/html-entity/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"&lt;b&gt;Vui&lt;/b&gt; &amp; &quot;Coding&quot;\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<b>Vui</b> & \\\"Coding\\\"")));
    }

    @Test
    void generateHashIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/hash/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Vui Coding\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "2d1ed5c88f1825c1a74cf6fec2e5b61455d542e5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "0b41e936e3341bc2ee9844992b5c6fbb69270b7a51877818d231ffd923aecbc9")));
    }

    @Test
    void serializePhpIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/php-serialize/serialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"{\\\"name\\\":\\\"DevKnowledge\\\",\\\"active\\\":true,\\\"count\\\":47}\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "a:3:{s:4:\\\"name\\\";s:12:\\\"DevKnowledge\\\";s:6:\\\"active\\\";b:1;s:5:\\\"count\\\";i:47;}")));
    }

    @Test
    void unserializePhpIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/php-serialize/unserialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"a:1:{s:4:\\\"name\\\";s:12:\\\"DevKnowledge\\\";}\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DevKnowledge")));
    }

    @Test
    void malformedSerializedPhpReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/php-serialize/unserialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"a:1:{\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_008")));
    }

    @Test
    void encodeHexIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/hex/encode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hi\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("48 69")));
    }

    @Test
    void decodeHexIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/hex/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"48 69\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hi")));
    }

    @Test
    void malformedHexReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/hex/decode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"zz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_009")));
    }

    @Test
    void debugJwtIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/jwt/debug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                                + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IlZ1aSBDb2RpbmciLCJpYXQiOjE1MTYyMzkwMjJ9"
                                + ".demo-signature\",\"minify\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HS256")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vui Coding")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("demo-signature")));
    }

    @Test
    void malformedJwtReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/jwt/debug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"only.two\",\"minify\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_010")));
    }

    @Test
    void testRegexpIsReachableWithNoAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/regexp/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"[\\\\w.+-]+@[\\\\w.-]+\\\\.[a-zA-Z]{2,}\",\"flags\":\"gi\","
                                + "\"testText\":\"hello@vuicoding.me\\nsupport@example.com\\nnot-an-email\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hello@vuicoding.me")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("support@example.com")));
    }

    @Test
    void malformedRegexReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/regexp/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"[unclosed\",\"flags\":\"g\",\"testText\":\"anything\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_011")));
    }

    @Test
    void malformedJsonReturns400ThroughTheSharedGlobalExceptionHandler() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"not valid json\",\"minify\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DEVUTILS_001")));
    }

    @Test
    void blankInputFailsBeanValidationWith400() throws Exception {
        mockMvc.perform(post("/api/v1/dev-utils/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"\",\"minify\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inputOverTheSizeCapFailsBeanValidationWith400() throws Exception {
        String oversizedInput = "1".repeat(DevUtilsLimits.MAX_INPUT_LENGTH + 1);

        mockMvc.perform(post("/api/v1/dev-utils/json/format")
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

        mockMvc.perform(post("/api/v1/dev-utils/json/format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"" + escapedForRequestBody + "\",\"minify\":true}"))
                .andExpect(status().isOk());
    }
}
