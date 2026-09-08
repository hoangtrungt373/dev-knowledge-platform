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
 * that every one of the 16 operation endpoints is genuinely reachable with no
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
