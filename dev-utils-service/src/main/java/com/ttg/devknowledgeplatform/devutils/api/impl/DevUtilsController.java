package com.ttg.devknowledgeplatform.devutils.api.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devutils.api.DevUtilsApi;
import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;
import com.ttg.devknowledgeplatform.devutils.dto.MinifiableTextRequest;
import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;
import com.ttg.devknowledgeplatform.devutils.dto.TextRequest;
import com.ttg.devknowledgeplatform.devutils.service.impl.CssOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.CsvToJsonOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.ErbOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HtmlBeautifyOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonFormatOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToCsvOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToPhpOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToYamlOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.LessOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.PhpToJsonOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.ScssOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.SqlFormatOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.StringCaseOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.XmlOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.YamlToJsonOperation;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link DevUtilsApi}. Each endpoint injects its operation by concrete type
 * rather than dispatching through an enum-keyed {@code DevUtilOperation} registry — see
 * {@code service.DevUtilOperation}'s own Javadoc for why: with one fixed REST endpoint per
 * operation, there is no runtime "which operation" decision left to make here.
 */
@RestController
@RequiredArgsConstructor
public class DevUtilsController implements DevUtilsApi {

    private final JsonFormatOperation jsonFormatOperation;
    private final YamlToJsonOperation yamlToJsonOperation;
    private final JsonToYamlOperation jsonToYamlOperation;
    private final HtmlBeautifyOperation htmlBeautifyOperation;
    private final CssOperation cssOperation;
    private final LessOperation lessOperation;
    private final ScssOperation scssOperation;
    private final JsOperation jsOperation;
    private final ErbOperation erbOperation;
    private final XmlOperation xmlOperation;
    private final JsonToCsvOperation jsonToCsvOperation;
    private final CsvToJsonOperation csvToJsonOperation;
    private final SqlFormatOperation sqlFormatOperation;
    private final PhpToJsonOperation phpToJsonOperation;
    private final JsonToPhpOperation jsonToPhpOperation;
    private final StringCaseOperation stringCaseOperation;

    @Override
    public ResponseEntity<DevUtilResponse> formatJson(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonFormatOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> yamlToJson(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(yamlToJsonOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> jsonToYaml(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonToYamlOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyHtml(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(htmlBeautifyOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyCss(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(cssOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyLess(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(lessOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyScss(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(scssOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyJs(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyErb(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(erbOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyXml(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(xmlOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> jsonToCsv(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonToCsvOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> csvToJson(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(csvToJsonOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> formatSql(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(sqlFormatOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> phpToJson(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(phpToJsonOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> jsonToPhp(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonToPhpOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<StringCaseResponse> convertStringCase(TextRequest request) {
        return ResponseEntity.ok(stringCaseOperation.execute(request.input()));
    }
}
