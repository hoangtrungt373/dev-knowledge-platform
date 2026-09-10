package com.ttg.devknowledgeplatform.devutils.api.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devutils.api.DevUtilsApi;
import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;
import com.ttg.devknowledgeplatform.devutils.dto.HashResponse;
import com.ttg.devknowledgeplatform.devutils.dto.MinifiableTextRequest;
import com.ttg.devknowledgeplatform.devutils.dto.RegexTestRequest;
import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;
import com.ttg.devknowledgeplatform.devutils.dto.TextRequest;
import com.ttg.devknowledgeplatform.devutils.service.impl.AsciiToHexOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.Base64DecodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.Base64EncodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.CronParserOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.CssOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.CsvToJsonOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.ErbOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HashGeneratorOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HexToAsciiOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HtmlBeautifyOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HtmlEntityDecodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.HtmlEntityEncodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonFormatOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToCsvOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToPhpOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToYamlOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JwtDebuggerOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.LessOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.PhpSerializeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.PhpToJsonOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.PhpUnserializeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.RegexTesterOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.ScssOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.SqlFormatOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.StringCaseOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.UrlDecodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.UrlEncodeOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.UrlParserOperation;
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
    private final Base64EncodeOperation base64EncodeOperation;
    private final Base64DecodeOperation base64DecodeOperation;
    private final UrlEncodeOperation urlEncodeOperation;
    private final UrlDecodeOperation urlDecodeOperation;
    private final HtmlEntityEncodeOperation htmlEntityEncodeOperation;
    private final HtmlEntityDecodeOperation htmlEntityDecodeOperation;
    private final HashGeneratorOperation hashGeneratorOperation;
    private final PhpSerializeOperation phpSerializeOperation;
    private final PhpUnserializeOperation phpUnserializeOperation;
    private final AsciiToHexOperation asciiToHexOperation;
    private final HexToAsciiOperation hexToAsciiOperation;
    private final JwtDebuggerOperation jwtDebuggerOperation;
    private final RegexTesterOperation regexTesterOperation;
    private final UrlParserOperation urlParserOperation;
    private final CronParserOperation cronParserOperation;

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

    @Override
    public ResponseEntity<DevUtilResponse> encodeBase64(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(base64EncodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> decodeBase64(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(base64DecodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> encodeUrl(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(urlEncodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> decodeUrl(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(urlDecodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> encodeHtmlEntity(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(htmlEntityEncodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> decodeHtmlEntity(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(htmlEntityDecodeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<HashResponse> generateHash(TextRequest request) {
        return ResponseEntity.ok(hashGeneratorOperation.execute(request.input()));
    }

    @Override
    public ResponseEntity<DevUtilResponse> serializePhp(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(phpSerializeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> unserializePhp(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(phpUnserializeOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> encodeHex(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(asciiToHexOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> decodeHex(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(hexToAsciiOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> debugJwt(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jwtDebuggerOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> testRegexp(RegexTestRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(
                regexTesterOperation.execute(request.pattern(), request.flags(), request.testText())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> parseUrl(MinifiableTextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(urlParserOperation.execute(request.input(), request.minify())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> parseCron(TextRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(cronParserOperation.execute(request.input())));
    }
}
