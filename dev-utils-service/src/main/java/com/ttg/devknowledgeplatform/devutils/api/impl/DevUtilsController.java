package com.ttg.devknowledgeplatform.devutils.api.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devutils.api.DevUtilsApi;
import com.ttg.devknowledgeplatform.devutils.dto.DevUtilRequest;
import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;
import com.ttg.devknowledgeplatform.devutils.service.impl.HtmlBeautifyOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonFormatOperation;
import com.ttg.devknowledgeplatform.devutils.service.impl.JsonToYamlOperation;
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

    @Override
    public ResponseEntity<DevUtilResponse> formatJson(DevUtilRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonFormatOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> yamlToJson(DevUtilRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(yamlToJsonOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> jsonToYaml(DevUtilRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(jsonToYamlOperation.execute(request.input())));
    }

    @Override
    public ResponseEntity<DevUtilResponse> beautifyHtml(DevUtilRequest request) {
        return ResponseEntity.ok(new DevUtilResponse(htmlBeautifyOperation.execute(request.input())));
    }
}
